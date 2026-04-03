package com.youdash.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private T data;

  private String message;
  private String messageKey;

  private Integer status;
  private Integer totalCount;

  private Boolean success;
}