package com.sentinelcv.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Centralized exception handling for all controllers.
 * Maps exceptions to appropriate HTTP responses and error pages.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SentinelCvException.class)
    public String handleSentinelCvException(SentinelCvException ex, Model model) {
        logger.error("SentinelCV error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        model.addAttribute("errorCode", ex.getErrorCode());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(DatasetParsingException.class)
    public String handleDatasetParsingException(DatasetParsingException ex, RedirectAttributes attrs) {
        logger.error("Dataset parsing error: {}", ex.getMessage(), ex);
        attrs.addFlashAttribute("error", "Dataset parsing failed: " + ex.getMessage());
        return "redirect:/datasets";
    }

    @ExceptionHandler(ModelLoadException.class)
    public String handleModelLoadException(ModelLoadException ex, RedirectAttributes attrs) {
        logger.error("Model load error: {}", ex.getMessage(), ex);
        attrs.addFlashAttribute("error", "Model loading failed: " + ex.getMessage());
        return "redirect:/models";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex, RedirectAttributes attrs) {
        logger.warn("Upload size exceeded: {}", ex.getMessage());
        attrs.addFlashAttribute("error", "File size exceeds the maximum allowed limit.");
        return "redirect:/dashboard";
    }

    @ExceptionHandler({org.springframework.core.task.TaskRejectedException.class, java.util.concurrent.RejectedExecutionException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String handleTaskRejected(Exception ex, Model model) {
        logger.warn("Request rejected due to thread pool saturation: {}", ex.getMessage());
        model.addAttribute("errorCode", "SERVICE_UNAVAILABLE");
        model.addAttribute("errorMessage", "The system is currently handling maximum concurrent audit load. Please try again shortly.");
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        model.addAttribute("errorCode", "BAD_REQUEST");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex, Model model) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        model.addAttribute("errorCode", "INTERNAL_ERROR");
        model.addAttribute("errorMessage", "An unexpected error occurred. Please check the logs.");
        return "error";
    }
}
