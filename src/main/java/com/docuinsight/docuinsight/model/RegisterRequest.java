/************Object to receive data from postman******************/
package com.docuinsight.docuinsight.model;

import lombok.Data;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
}
