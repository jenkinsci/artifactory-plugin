package org.jfrog.hudson.util;

import com.google.common.collect.Lists;

import java.util.List;

public class ErrorResponse {
    List<Error> errors = Lists.newArrayList();

    public ErrorResponse(int status, String message) {
        errors.add(new Error(status, message != null ? message : ""));
    }

    public List<Error> getErrors() {
        return errors;
    }

    private static class Error {
        private int status = 500;
        private String message = "";

        private Error(int status) {
            this.status = status;
        }

        private Error(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

}
