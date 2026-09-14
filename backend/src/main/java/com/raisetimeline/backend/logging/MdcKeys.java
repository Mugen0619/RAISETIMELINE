package com.raisetimeline.backend.logging;

/**
 * ログに自動的に含めるMDCキー名。フィルター間で名前を統一するための定数。
 */
public final class MdcKeys {

	public static final String TRACE_ID = "traceId";
	public static final String USER_ID = "userId";

	private MdcKeys() {
	}
}
