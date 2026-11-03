package com.sentinelcv.util;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

/**
 * Helper executable invoked by testSeparateJvmExtractionLockConflict in a genuinely separate OS process.
 * Acquires a real kernel FileLock on the target lock file, outputs LOCKED to stdout,
 * and waits on stdin before releasing the lock and exiting.
 */
public class ExternalProcessLockRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ExternalProcessLockRunner <destNormalizedPath>");
            System.exit(1);
        }

        String destNormalizedPath = args[0];
        Path lockDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinelcv_locks");
        Files.createDirectories(lockDir);
        String lockKey = HashUtil.sha256(destNormalizedPath);
        Path lockFilePath = lockDir.resolve("extract_" + lockKey + ".lock");

        FileChannel channel = FileChannel.open(lockFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock();
        if (lock != null) {
            System.out.println("LOCKED");
            System.out.flush();

            // Wait for signal on stdin
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                scanner.nextLine();
            }

            lock.release();
            channel.close();
            System.out.println("RELEASED");
            System.out.flush();
            System.exit(0);
        } else {
            System.err.println("FAILED_TO_LOCK");
            System.exit(2);
        }
    }
}

