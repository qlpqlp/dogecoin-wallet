/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.android.LogcatAppender;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Andreas Schildbach
 */
public class Logging {
    private static final String LOG_DIRECTORY_NAME = "log";
    private static final String LOG_FILE_NAME = "wallet.log";
    private static final String LOG_ROLLING_FILE_NAME_PATTERN = "wallet.%d{yyyy-MM-dd,UTC}.log.gz";

    private static File logFile;
    private static LoggerContext loggerContext;

    public static synchronized void init(final File filesDir, final Context context) {
        if (logFile != null)
            return;

        // create log dir
        final File logDir = new File(filesDir, LOG_DIRECTORY_NAME);
        logDir.mkdir();
        logFile = new File(logDir, LOG_FILE_NAME);

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder filePattern = new PatternLayoutEncoder();
        filePattern.setContext(loggerContext);
        filePattern.setPattern("%d{HH:mm:ss,UTC} [%thread] %logger{0} - %msg%n");
        filePattern.start();

        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(loggerContext);
        fileAppender.setFile(logFile.getAbsolutePath());

        final TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logDir.getAbsolutePath() + "/" + LOG_ROLLING_FILE_NAME_PATTERN);
        rollingPolicy.setMaxHistory(7);
        rollingPolicy.start();

        fileAppender.setEncoder(filePattern);
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.start();

        final PatternLayoutEncoder logcatTagPattern = new PatternLayoutEncoder();
        logcatTagPattern.setContext(loggerContext);
        logcatTagPattern.setPattern("%logger{0}");
        logcatTagPattern.start();

        final PatternLayoutEncoder logcatPattern = new PatternLayoutEncoder();
        logcatPattern.setContext(loggerContext);
        logcatPattern.setPattern("[%thread] %msg%n");
        logcatPattern.start();

        final LogcatAppender logcatAppender = new LogcatAppender();
        logcatAppender.setContext(loggerContext);
        logcatAppender.setTagEncoder(logcatTagPattern);
        logcatAppender.setEncoder(logcatPattern);
        logcatAppender.start();

        final ch.qos.logback.classic.Logger log = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        log.addAppender(fileAppender);
        log.addAppender(logcatAppender);
        
        // Set initial log level based on preference
        updateLogLevel(context);
    }

    /**
     * Update log level based on the enable_logging preference.
     * If logging is disabled, set level to OFF to prevent log growth.
     * If logging is enabled, set level to INFO for normal logging.
     */
    public static synchronized void updateLogLevel(final Context context) {
        if (loggerContext == null)
            return;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean enableLogging = prefs.getBoolean(Configuration.PREFS_KEY_ENABLE_LOGGING, false);
        
        final ch.qos.logback.classic.Logger log = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        if (enableLogging) {
            log.setLevel(Level.INFO);
        } else {
            log.setLevel(Level.OFF);
        }
    }
}
