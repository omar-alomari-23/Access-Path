package com.accesspath.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordinateValidator implements
        ConstraintValidator<ValidCoordinates, HasCoordinates> {

    @Override
    public boolean isValid(
            HasCoordinates request,
            ConstraintValidatorContext context) {

        if (request == null) return true;

        if (request.getLatitude() == null || 
            request.getLongitude() == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Both latitude and longitude are required"
            ).addPropertyNode("latitude").addConstraintViolation();
            return false;
        }

        if (Double.isNaN(request.getLatitude()) ||
            request.getLatitude() < -90 ||
            request.getLatitude() > 90) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Latitude must be between -90 and 90"
            ).addPropertyNode("latitude").addConstraintViolation();
            return false;
        }

        if (Double.isNaN(request.getLongitude()) ||
            request.getLongitude() < -180 ||
            request.getLongitude() > 180) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Longitude must be between -180 and 180"
            ).addPropertyNode("longitude").addConstraintViolation();
            return false;
        }

        return true;
    }
}