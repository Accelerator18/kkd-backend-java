package ru.kkd.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.kkd.api.ApiModels.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException e) { return error(e.status(),e.code(),e.getMessage()); }
    @ExceptionHandler({MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,HandlerMethodValidationException.class})
    public ResponseEntity<ErrorBody> validation(Exception e) {
        String message = "Некорректные параметры запроса";
        if (e instanceof MissingServletRequestParameterException missing) message = "Обязательный параметр: " + missing.getParameterName();
        if (e instanceof MethodArgumentTypeMismatchException mismatch) message = "Некорректный тип параметра: " + mismatch.getName();
        if (e instanceof HttpMessageNotReadableException) message = "Некорректный JSON: проверьте поля и формат даты YYYY-MM-DD";
        return error(422,"VALIDATION_ERROR",message);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> notFound(Exception e) { return error(404,"ENDPOINT_NOT_FOUND","Метод API не найден"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> method(Exception e) { return error(405,"METHOD_NOT_ALLOWED","HTTP-метод не поддерживается"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorBody> media(Exception e) { return error(415,"UNSUPPORTED_MEDIA_TYPE","Ожидается application/json"); }
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorBody> accept(Exception e) { return error(406,"NOT_ACCEPTABLE","Ответ доступен в application/json"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> internal(Exception e) {
        log.error("Ошибка обработки запроса",e);
        return error(500,"INTERNAL_ERROR","Внутренняя ошибка сервиса");
    }
    private ResponseEntity<ErrorBody> error(int status, String code, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ErrorBody(new ErrorDetail(code,message)));
    }
}
