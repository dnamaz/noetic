package com.dnamaz.websearch.model;

/**
 * A page interaction action to execute before content extraction.
 * Used with dynamic fetching (Jvppeteer/CDP).
 */
public record PageAction(
        ActionType type,
        String selector,
        String value,
        int delayMs
) {
    public static PageAction click(String selector) {
        return new PageAction(ActionType.CLICK, selector, null, 0);
    }

    public static PageAction type(String selector, String text) {
        return new PageAction(ActionType.TYPE, selector, text, 0);
    }

    public static PageAction scroll(int pixels) {
        return new PageAction(ActionType.SCROLL, null, String.valueOf(pixels), 0);
    }

    public static PageAction wait(int ms) {
        return new PageAction(ActionType.WAIT, null, String.valueOf(ms), 0);
    }

    public static PageAction waitForSelector(String selector) {
        return new PageAction(ActionType.WAIT_FOR_SELECTOR, selector, null, 0);
    }
}
