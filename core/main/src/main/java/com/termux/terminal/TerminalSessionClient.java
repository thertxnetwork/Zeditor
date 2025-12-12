package com.termux.terminal;

import androidx.annotation.Nullable;

/**
 * The interface for communication between the terminal emulator and its client.
 * Used to send callbacks when terminal state changes or for logging.
 * 
 * Modified from original Termux version to remove TerminalSession dependency
 * since we use TerminalEmulator directly for SSH without local PTY.
 */
public interface TerminalSessionClient {

    void onTextChanged();

    void onTitleChanged();

    void onSessionFinished();

    void onCopyTextToClipboard(String text);

    void onPasteTextFromClipboard();

    void onBell();

    void onColorsChanged();

    void onTerminalCursorStateChange(boolean state);

    void setTerminalShellPid(int pid);

    Integer getTerminalCursorStyle();

    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
