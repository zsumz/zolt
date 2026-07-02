package sh.zolt.cli;

import sh.zolt.cli.console.ColorMode;
import java.util.Locale;
import picocli.CommandLine;

public final class ColorModeConverter implements CommandLine.ITypeConverter<ColorMode> {
    @Override
    public ColorMode convert(String value) {
        try {
            return ColorMode.from(value);
        } catch (IllegalArgumentException exception) {
            String modes = String.join(
                    ", ",
                    ColorMode.AUTO.toString(),
                    ColorMode.ALWAYS.toString(),
                    ColorMode.NEVER.toString());
            throw new CommandLine.TypeConversionException(
                    "expected one of: " + modes + " (was `" + value.toLowerCase(Locale.ROOT) + "`)");
        }
    }
}
