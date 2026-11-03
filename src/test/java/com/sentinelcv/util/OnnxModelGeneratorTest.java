package com.sentinelcv.util;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class OnnxModelGeneratorTest {

    static class ProtoWriter {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        public void writeVarint(long value) {
            while ((value & ~0x7FL) != 0) {
                baos.write((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            baos.write((int) (value & 0x7F));
        }

        public void writeTag(int fieldNumber, int wireType) {
            writeVarint(((long) fieldNumber << 3) | wireType);
        }

        public void writeInt64(int fieldNumber, long value) {
            writeTag(fieldNumber, 0);
            writeVarint(value);
        }

        public void writeString(int fieldNumber, String s) {
            byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeTag(fieldNumber, 2);
            writeVarint(bytes.length);
            baos.write(bytes, 0, bytes.length);
        }

        public void writeBytes(int fieldNumber, byte[] bytes) {
            writeTag(fieldNumber, 2);
            writeVarint(bytes.length);
            baos.write(bytes, 0, bytes.length);
        }

        public void writeMessage(int fieldNumber, ProtoWriter msg) {
            writeBytes(fieldNumber, msg.toByteArray());
        }

        public byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    public static byte[] buildValidOnnxModel(long[] shape, String opType) throws IOException {
        // Build Dimension message
        ProtoWriter graph = new ProtoWriter();

        // 1. NodeProto (Relu or Identity)
        ProtoWriter node = new ProtoWriter();
        node.writeString(1, "input");   // input name
        node.writeString(2, "output");  // output name
        node.writeString(3, "op0");     // node name
        node.writeString(4, opType);    // op_type: e.g. "Relu" or "Identity"
        graph.writeMessage(1, node);

        // 2. Graph name
        graph.writeString(2, "sentinelcv_test_graph");

        // Helper to build TensorShapeProto
        ProtoWriter tensorShape = new ProtoWriter();
        for (long dim : shape) {
            ProtoWriter dimMsg = new ProtoWriter();
            dimMsg.writeInt64(1, dim); // dim_value = dim
            tensorShape.writeMessage(1, dimMsg);
        }

        // Helper to build TypeProto (TensorProto.FLOAT = 1)
        ProtoWriter tensorType = new ProtoWriter();
        tensorType.writeInt64(1, 1); // elem_type = FLOAT
        tensorType.writeMessage(2, tensorShape); // shape

        ProtoWriter typeProto = new ProtoWriter();
        typeProto.writeMessage(1, tensorType);

        // 3. Graph input ValueInfoProto
        ProtoWriter inputInfo = new ProtoWriter();
        inputInfo.writeString(1, "input");
        inputInfo.writeMessage(2, typeProto);
        graph.writeMessage(11, inputInfo);

        // 4. Graph output ValueInfoProto
        ProtoWriter outputInfo = new ProtoWriter();
        outputInfo.writeString(1, "output");
        outputInfo.writeMessage(2, typeProto);
        graph.writeMessage(12, outputInfo);

        // ModelProto
        ProtoWriter model = new ProtoWriter();
        model.writeInt64(1, 8); // ir_version = 8 (compatible with opset 13-17)
        model.writeString(2, "SentinelCV-Auditor");
        model.writeString(3, "1.0.0");
        model.writeString(4, "com.sentinelcv");
        model.writeInt64(5, 1); // model_version

        // Opset import: default domain, version 17
        ProtoWriter opset = new ProtoWriter();
        opset.writeString(1, ""); // default domain
        opset.writeInt64(2, 17);  // opset version 17
        model.writeMessage(8, opset);

        // Attach graph
        model.writeMessage(7, graph);

        return model.toByteArray();
    }

    @Test
    void testGenerateAndRunRealOnnxModel(@TempDir Path tempDir) throws Exception {
        long[] shape = new long[]{1, 3, 224, 224};
        byte[] onnxBytes = buildValidOnnxModel(shape, "Relu");
        assertNotNull(onnxBytes);
        assertTrue(onnxBytes.length > 50, "Generated ONNX model should be well-formed protobuf");

        File modelFile = tempDir.resolve("test_model.onnx").toFile();
        try (FileOutputStream fos = new FileOutputStream(modelFile)) {
            fos.write(onnxBytes);
        }

        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions())) {
            assertNotNull(session);
            assertEquals(1, session.getInputNames().size());
            assertEquals("input", session.getInputNames().iterator().next());
            assertEquals(1, session.getOutputNames().size());
            assertEquals("output", session.getOutputNames().iterator().next());

            int totalElements = 1 * 3 * 224 * 224;
            float[] inputData = new float[totalElements];
            for (int i = 0; i < totalElements; i++) {
                inputData[i] = (i % 2 == 0) ? 0.5f : -0.5f;
            }

            FloatBuffer buffer = FloatBuffer.wrap(inputData);
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, buffer, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap("input", tensor))) {
                assertNotNull(result);
                Object val = result.get(0).getValue();
                assertNotNull(val);
                // Relu output: negative values become 0.0, positive stay 0.5
                float[][][][] out4d = (float[][][][]) val;
                assertEquals(0.5f, out4d[0][0][0][0], 1e-4);
                assertEquals(0.0f, out4d[0][0][0][1], 1e-4);
            }
        }
    }
}
