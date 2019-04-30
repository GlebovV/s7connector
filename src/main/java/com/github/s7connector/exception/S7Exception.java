/*
Copyright 2016 S7connector members (github.com/s7connector)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.github.s7connector.exception;

import com.github.s7connector.impl.nodave.Nodave;

import java.io.IOException;
import java.util.Optional;

import static com.github.s7connector.impl.nodave.Nodave.RESULT_OK;

/**
 * The Class S7Exception is an exception related to S7 Communication
 */
public final class S7Exception extends IOException {

	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = -4761415733559374116L;

	private final int errorCode;

	/**
	 * Instantiates a new s7 exception.
	 */
	public S7Exception() {
	    errorCode = RESULT_OK;
	}

	/**
	 * Instantiates a new s7 exception.
	 *
	 * @param message
	 *            the message
	 */
	public S7Exception(final String message) {
		super(message);
        errorCode = RESULT_OK;
    }

	/**
	 * Instantiates a new s7 exception.
	 *
	 * @param message
	 *            the message
	 * @param cause
	 *            the cause
	 */
	public S7Exception(final String message, final Throwable cause) {
		super(message, cause);
        errorCode = RESULT_OK;
    }

	/**
	 * Instantiates a new s7 exception.
	 *
	 * @param cause
	 *            the cause
	 */
	public S7Exception(final Throwable cause) {
		super(cause);
        errorCode = RESULT_OK;
    }


    /**
     * Instantiates a new s7 exception.
     *
     * @param errorCode
     *            the error code
     */
    public S7Exception(final int errorCode) {
        super(Nodave.strerror(errorCode));
        this.errorCode = errorCode;

    }

    Optional<Integer> getErrorCode() {
        if (errorCode != RESULT_OK)
            return Optional.of(errorCode);
        else
            return Optional.empty();
    }

}
