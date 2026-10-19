package com.example.solimus.exceptions;

import com.example.solimus.dtos.auth.ErrorResponseDTO;
import com.example.solimus.dtos.syndic.owner.CoOwnerConflictResponseDTO;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadRequest(BadRequestException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Valeur invalide pour le paramètre '" + ex.getName() + "' : " + ex.getValue();
        ErrorResponseDTO error = new ErrorResponseDTO(
                message,
                null,
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PhoneAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handlePhoneAlreadyExists(PhoneAlreadyExistsException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDTO> handleConflict(ConflictException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientBalance(InsufficientBalanceException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentGateway(PaymentGatewayException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.BAD_GATEWAY.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbidden(ForbiddenException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                "Cette fonctionnalité n'est pas incluse dans votre formule d'abonnement actuelle. Passez à une formule supérieure pour y accéder.",
                null,
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                ex.getMessage(),
                null,
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value()
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            details.add(fieldName + ": " + errorMessage);
        });

        ErrorResponseDTO error = new ErrorResponseDTO(
                "Erreurs de validation",
                details,
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Validation sur des @RequestParam/@PathVariable individuels (nécessite @Validated sur le
    // contrôleur) — distinct de MethodArgumentNotValidException, qui ne couvre que @Valid @RequestBody
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        ErrorResponseDTO error = new ErrorResponseDTO(
                "Erreurs de validation",
                details,
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Corps de requête illisible : JSON mal formé (virgule manquante, nombre 0123...), type
    // incompatible ("abc" pour un nombre), valeur d'énumération inconnue, body absent. C'est une
    // erreur du client → 400, avec un message clair et sans détail interne (pas de trace Jackson).
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException ex) {
        String detail;
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof InvalidFormatException ife && !ife.getPath().isEmpty()) {
            detail = "Valeur invalide pour le champ '" + jsonPath(ife.getPath()) + "'"
                    + (ife.getTargetType() != null && ife.getTargetType().isEnum()
                    ? " (valeurs acceptées : " + Arrays.toString(ife.getTargetType().getEnumConstants()) + ")"
                    : "");
        } else if (cause instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            detail = "Type invalide pour le champ '" + jsonPath(mie.getPath()) + "'";
        } else if (cause instanceof JsonParseException) {
            detail = "JSON mal formé : vérifiez les virgules, guillemets, accolades et le format des nombres (pas de zéro initial, ex. 123 et non 0123)";
        } else {
            detail = "Corps de requête manquant ou illisible";
        }
        log.warn("Requête illisible : {}", detail);

        ErrorResponseDTO error = new ErrorResponseDTO(
                "Requête invalide",
                List.of(detail),
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Paramètre de requête obligatoire absent (ex: ?subscriberType=) → 400 au lieu de 500
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParam(MissingServletRequestParameterException ex) {
        ErrorResponseDTO error = new ErrorResponseDTO(
                "Paramètre obligatoire manquant : '" + ex.getParameterName() + "'",
                null,
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private static String jsonPath(List<JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : path) {
            if (ref.getFieldName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    @ExceptionHandler(CoOwnerAlreadyExistsException.class)
    public ResponseEntity<CoOwnerConflictResponseDTO> handleCoOwnerAlreadyExists(CoOwnerAlreadyExistsException ex) {
        CoOwnerConflictResponseDTO error = new CoOwnerConflictResponseDTO(
                ex.getMessage(),      // "Email déjà utilisé"
                ex.getCoOwnerId(),    // ID du copropriétaire existant
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value() // 409
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Internal Server Error: ", ex);
        List<String> details = new ArrayList<>();
        details.add(ex.getMessage());
        ErrorResponseDTO error = new ErrorResponseDTO(
                "Une erreur inattendue est survenue",
                details,
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
