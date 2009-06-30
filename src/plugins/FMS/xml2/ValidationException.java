package plugins.FMS.xml2;

public class ValidationException extends Exception {
	public ValidationException(String msg) {
		super(msg);
	}

	public ValidationException(Throwable cause) {
		super(cause);
	}

	public ValidationException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
