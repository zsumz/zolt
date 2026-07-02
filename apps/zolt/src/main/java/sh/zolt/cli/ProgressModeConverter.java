package sh.zolt.cli;

import sh.zolt.cli.console.ProgressMode;
import java.util.Locale;
import picocli.CommandLine;

public final class ProgressModeConverter implements CommandLine.ITypeConverter<ProgressMode> {
    @Override
    public ProgressMode convert(String value) {
        try {
            return ProgressMode.from(value);
        } catch (IllegalArgumentException exception) {
            String modes = String.join(
                    ", ",
                    ProgressMode.AUTO.toString(),
                    ProgressMode.ALWAYS.toString(),
                    ProgressMode.NEVER.toString());
            throw new CommandLine.TypeConversionException(
                    "expected one of: " + modes + " (was `" + value.toLowerCase(Locale.ROOT) + "`)");
        }
    }
}
