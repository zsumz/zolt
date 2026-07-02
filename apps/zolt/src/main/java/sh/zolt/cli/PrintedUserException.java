package sh.zolt.cli;

import picocli.CommandLine;

public final class PrintedUserException extends CommandLine.ExecutionException {
    private static final String MARKER = "[zolt-printed-user-error] ";

    public PrintedUserException(CommandLine commandLine, String message) {
        super(commandLine, marked(message));
    }

    public static boolean alreadyPrinted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof PrintedUserException
                    || current.getMessage() != null && current.getMessage().startsWith(MARKER)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String marked(String message) {
        return MARKER + (message == null ? "" : message);
    }
}
