package com.example.campushub.exceptions;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.campushub.responses.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandle {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(ApiResponse.error(String.join(", ", errors)));
    }

    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataNotFoundException(DataNotFoundException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        ex.printStackTrace();
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Internal Server Error: " + msg));
    }

    @ExceptionHandler(InvalidParamException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParamException(InvalidParamException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Object>> handleNullPointerException(NullPointerException ex) {
        ex.printStackTrace(); // In ra console Ä‘á»ƒ debug
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Null Pointer Exception vá»›i message lĂ  null. Vui lĂ²ng kiá»ƒm tra server logs!";
        StackTraceElement[] stackTrace = ex.getStackTrace();

        // Kiểm tra xem lỗi có phải từ việc gọi method trên currentUser null không
        if (stackTrace != null && stackTrace.length > 0) {
            String methodName = stackTrace[0].getMethodName();
            // Nếu lỗi xảy ra khi gọi getEmail(), getId(), etc. từ currentUser
            if (methodName.equals("because \\\"user\\\" is null")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(
                                "Bạn cần đăng nhập để thực hiện chức năng này. Token không hợp lệ hoặc đã hết hạn."));
            }
        }

        // Fallback cho các NullPointerException khác
        if (errorMessage != null && errorMessage.contains("User.getId()")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại"));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(errorMessage));
    }

}
