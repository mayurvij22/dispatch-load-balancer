package com.dispatch.dto;

/**
 * Generic acknowledgement body, e.g. {@code {"message": "Delivery orders accepted.", "status": "success"}}.
 */
public record ApiResponse(String message, String status) {

    public static final String SUCCESS = "success";

    public static ApiResponse success(String message) {
        return new ApiResponse(message, SUCCESS);
    }
}
