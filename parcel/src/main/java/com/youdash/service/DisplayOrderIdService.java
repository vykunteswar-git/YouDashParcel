package com.youdash.service;

public interface DisplayOrderIdService {

    /** Allocates the next short reference, e.g. {@code YP-1000}. */
    String allocateNext();
}
