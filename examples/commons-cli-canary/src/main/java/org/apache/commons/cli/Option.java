package org.apache.commons.cli;

/**
 * Describes a command-line option.
 */
public final class Option {
    private final String opt;
    private final String longOpt;
    private final String description;
    private final boolean hasArg;
    private final boolean required;

    private Option(Builder builder) {
        this.opt = builder.opt;
        this.longOpt = builder.longOpt;
        this.description = builder.description;
        this.hasArg = builder.hasArg;
        this.required = builder.required;
    }

    /**
     * Creates a builder for a short option.
     *
     * @param opt short option name without the leading dash
     * @return option builder
     */
    public static Builder builder(String opt) {
        return new Builder(opt);
    }

    /**
     * Returns the short option name.
     *
     * @return short option name
     */
    public String opt() {
        return opt;
    }

    /**
     * Returns the long option name.
     *
     * @return long option name, or an empty string
     */
    public String longOpt() {
        return longOpt;
    }

    /**
     * Returns the help description.
     *
     * @return description, or an empty string
     */
    public String description() {
        return description;
    }

    /**
     * Returns true when this option consumes the next argument.
     *
     * @return whether this option has an argument
     */
    public boolean hasArg() {
        return hasArg;
    }

    /**
     * Returns true when this option is required.
     *
     * @return whether this option is required
     */
    public boolean required() {
        return required;
    }

    boolean matches(String token) {
        return token.equals("-" + opt) || (!longOpt.isEmpty() && token.equals("--" + longOpt));
    }

    /**
     * Builder for immutable options.
     */
    public static final class Builder {
        private final String opt;
        private String longOpt = "";
        private String description = "";
        private boolean hasArg;
        private boolean required;

        private Builder(String opt) {
            if (opt == null || opt.trim().isEmpty() || opt.startsWith("-")) {
                throw new IllegalArgumentException("Option name must be non-empty and omit leading dashes.");
            }
            this.opt = opt;
        }

        /**
         * Sets the long option name.
         *
         * @param longOpt long option name without leading dashes
         * @return this builder
         */
        public Builder longOpt(String longOpt) {
            if (longOpt == null || longOpt.trim().isEmpty() || longOpt.startsWith("-")) {
                throw new IllegalArgumentException("Long option name must be non-empty and omit leading dashes.");
            }
            this.longOpt = longOpt;
            return this;
        }

        /**
         * Marks this option as requiring an argument.
         *
         * @return this builder
         */
        public Builder hasArg() {
            this.hasArg = true;
            return this;
        }

        /**
         * Marks this option as required.
         *
         * @return this builder
         */
        public Builder required() {
            this.required = true;
            return this;
        }

        /**
         * Sets the help description.
         *
         * @param description help description
         * @return this builder
         */
        public Builder desc(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        /**
         * Builds the option.
         *
         * @return immutable option
         */
        public Option build() {
            return new Option(this);
        }
    }
}
