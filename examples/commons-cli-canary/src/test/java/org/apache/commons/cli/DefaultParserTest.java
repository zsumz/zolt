package org.apache.commons.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class DefaultParserTest {
    @Test
    public void parsesShortAndLongOptions() throws Exception {
        Options options = new Options()
                .addOption(Option.builder("f").longOpt("file").hasArg().desc("Input file").build())
                .addOption(Option.builder("v").longOpt("verbose").desc("Verbose output").build());

        CommandLine commandLine = new DefaultParser().parse(options, new String[] {"--file", "demo.txt", "-v"});

        assertTrue(commandLine.hasOption("f"));
        assertTrue(commandLine.hasOption("file"));
        assertTrue(commandLine.hasOption("verbose"));
        assertEquals("demo.txt", commandLine.getOptionValue("f"));
        assertEquals("demo.txt", commandLine.getOptionValue("file"));
    }

    @Test
    public void rejectsMissingArgument() throws Exception {
        Options options = new Options()
                .addOption(Option.builder("f").longOpt("file").hasArg().build());

        try {
            new DefaultParser().parse(options, new String[] {"--file"});
            fail("expected ParseException");
        } catch (ParseException exception) {
            assertEquals("Missing argument for option `--file`.", exception.getMessage());
        }
    }

    @Test
    public void rejectsMissingRequiredOption() throws Exception {
        Options options = new Options()
                .addOption(Option.builder("f").longOpt("file").hasArg().required().build());

        try {
            new DefaultParser().parse(options, new String[0]);
            fail("expected ParseException");
        } catch (ParseException exception) {
            assertEquals("Missing required option `-f`.", exception.getMessage());
        }
    }
}
