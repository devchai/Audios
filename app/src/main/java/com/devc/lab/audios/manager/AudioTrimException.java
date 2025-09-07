package com.devc.lab.audios.manager;

/**
 * 오디오 자르기 작업 전용 예외 클래스
 * 구체적인 오류 유형과 사용자 친화적인 메시지 제공
 */
public class AudioTrimException extends Exception {
    
    /**
     * 오디오 자르기 오류 유형
     */
    public enum ErrorType {
        // 입력 관련 오류
        INVALID_INPUT_URI("입력 파일이 유효하지 않습니다"),
        INVALID_TIME_RANGE("시작/종료 시간이 잘못되었습니다"),
        UNSUPPORTED_FORMAT("지원하지 않는 오디오 포맷입니다"),
        
        // 파일 시스템 오류  
        FILE_NOT_FOUND("파일을 찾을 수 없습니다"),
        FILE_ACCESS_DENIED("파일에 접근할 수 없습니다"),
        INSUFFICIENT_STORAGE("저장 공간이 부족합니다"),
        OUTPUT_PATH_INVALID("출력 경로가 유효하지 않습니다"),
        
        // 미디어 처리 오류
        MEDIA_EXTRACTOR_FAILED("미디어 파일 분석에 실패했습니다"),
        MEDIA_MUXER_FAILED("미디어 파일 생성에 실패했습니다"),
        AUDIO_TRACK_NOT_FOUND("오디오 트랙을 찾을 수 없습니다"),
        TRACK_FORMAT_INCOMPATIBLE("오디오 포맷이 호환되지 않습니다"),
        
        // 프로세싱 오류
        PROCESSING_INTERRUPTED("작업이 중단되었습니다"),
        PROCESSING_TIMEOUT("작업 시간이 초과되었습니다"),
        MEMORY_ERROR("메모리가 부족합니다"),
        
        // 시스템 오류
        UNKNOWN_ERROR("알 수 없는 오류가 발생했습니다");
        
        private final String userMessage;
        
        ErrorType(String userMessage) {
            this.userMessage = userMessage;
        }
        
        public String getUserMessage() {
            return userMessage;
        }
    }
    
    private final ErrorType errorType;
    private final String technicalDetails;
    
    /**
     * 기본 생성자
     * @param errorType 오류 유형
     */
    public AudioTrimException(ErrorType errorType) {
        super(errorType.getUserMessage());
        this.errorType = errorType;
        this.technicalDetails = null;
    }
    
    /**
     * 기술적 세부사항 포함 생성자
     * @param errorType 오류 유형
     * @param technicalDetails 기술적 세부사항
     */
    public AudioTrimException(ErrorType errorType, String technicalDetails) {
        super(errorType.getUserMessage() + (technicalDetails != null ? ": " + technicalDetails : ""));
        this.errorType = errorType;
        this.technicalDetails = technicalDetails;
    }
    
    /**
     * 원본 예외 포함 생성자
     * @param errorType 오류 유형
     * @param cause 원본 예외
     */
    public AudioTrimException(ErrorType errorType, Throwable cause) {
        super(errorType.getUserMessage(), cause);
        this.errorType = errorType;
        this.technicalDetails = cause != null ? cause.getMessage() : null;
    }
    
    /**
     * 완전한 생성자
     * @param errorType 오류 유형
     * @param technicalDetails 기술적 세부사항
     * @param cause 원본 예외
     */
    public AudioTrimException(ErrorType errorType, String technicalDetails, Throwable cause) {
        super(errorType.getUserMessage() + (technicalDetails != null ? ": " + technicalDetails : ""), cause);
        this.errorType = errorType;
        this.technicalDetails = technicalDetails;
    }
    
    /**
     * 오류 유형 반환
     * @return ErrorType
     */
    public ErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * 사용자용 오류 메시지 반환
     * @return 사용자 친화적인 오류 메시지
     */
    public String getUserMessage() {
        return errorType.getUserMessage();
    }
    
    /**
     * 기술적 세부사항 반환
     * @return 기술적 세부사항 또는 null
     */
    public String getTechnicalDetails() {
        return technicalDetails;
    }
    
    /**
     * 완전한 오류 정보 반환 (로깅용)
     * @return 완전한 오류 정보
     */
    public String getFullErrorInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("AudioTrimException: ");
        sb.append(errorType.name()).append(" - ");
        sb.append(errorType.getUserMessage());
        
        if (technicalDetails != null) {
            sb.append(" (").append(technicalDetails).append(")");
        }
        
        if (getCause() != null) {
            sb.append(" | Cause: ").append(getCause().getClass().getSimpleName());
            sb.append(" - ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }
    
    /**
     * 일반 Exception에서 AudioTrimException으로 변환
     * @param cause 원본 예외
     * @return AudioTrimException
     */
    public static AudioTrimException fromException(Exception cause) {
        if (cause instanceof AudioTrimException) {
            return (AudioTrimException) cause;
        }
        
        // 원본 예외 유형에 따라 적절한 ErrorType 결정
        if (cause instanceof java.io.FileNotFoundException) {
            return new AudioTrimException(ErrorType.FILE_NOT_FOUND, cause);
        } else if (cause instanceof java.io.IOException) {
            return new AudioTrimException(ErrorType.FILE_ACCESS_DENIED, cause);
        } else if (cause instanceof IllegalArgumentException) {
            return new AudioTrimException(ErrorType.INVALID_INPUT_URI, cause);
        } else if (cause instanceof InterruptedException) {
            return new AudioTrimException(ErrorType.PROCESSING_INTERRUPTED, cause);
        } else {
            return new AudioTrimException(ErrorType.UNKNOWN_ERROR, cause);
        }
    }
    
    /**
     * Throwable(Error 포함)에서 AudioTrimException으로 변환
     * @param cause 원본 Throwable
     * @return AudioTrimException
     */
    public static AudioTrimException fromThrowable(Throwable cause) {
        if (cause instanceof AudioTrimException) {
            return (AudioTrimException) cause;
        }
        
        if (cause instanceof OutOfMemoryError) {
            return new AudioTrimException(ErrorType.MEMORY_ERROR, "메모리 부족", cause);
        } else if (cause instanceof Exception) {
            return fromException((Exception) cause);
        } else {
            return new AudioTrimException(ErrorType.UNKNOWN_ERROR, cause.getMessage(), cause);
        }
    }
}