package cn.peixun.demo;

public class BusinessException extends RuntimeException {
  public final int status;
  public final String code;

  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new BusinessException(422, "VALIDATION_FAILED", message);
  }
}
