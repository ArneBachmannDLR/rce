/*
 * Copyright (C) 2006-2015 DLR, Germany
 * 
 * All rights reserved
 * 
 * http://www.rcenvironment.de/
 */

package de.rcenvironment.core.gui.workflow.executor.properties;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

/**
 * Helper class to make whitespace,tab and carriageReturn visible in StyledText.
 * 
 * @author Goekhan Guerkan
 */
public class WhitespaceShowListener implements PaintListener {

    private static final char SPACE_SIGN = '\u00b7';

    private static final char IDEOGRAPHIC_SPACE_SIGN = '\u00b0';

    private static final char TAB_SIGN = '\u00bb';

    private static final char CARRIAGE_RETURN_SIGN = '\u00a4';

    private static final char LINE_FEED_SIGN = '\u00b6';

    private StyledText scriptingText;

    private boolean on;

    public WhitespaceShowListener() {
        on = false;
    }

    /**
     * Checks if script for whitespace and redraws showing whitespace.
     * 
     * @param gc drawing capabilities for Control
     */
    public void handleDrawRequest(GC gc) {
        if (on) {
            StyledTextContent content = scriptingText.getContent();
            int length = scriptingText.getCharCount();
            String text = content.getTextRange(0, length);
            StyleRange styleRange = null;
            Color fg = null;
            Point selection = scriptingText.getSelection();
            StringBuffer visibleChar = new StringBuffer(10);
            for (int textOffset = 0; textOffset <= length; ++textOffset) {
                int delta = 0;
                boolean eol = false;
                if (textOffset < length) {
                    delta = 1;
                    char c = text.charAt(textOffset);
                    switch (c) {
                    case ' ':
                        visibleChar.append(SPACE_SIGN);
                        break;
                    case '\u3000':
                        visibleChar.append(IDEOGRAPHIC_SPACE_SIGN);
                        break;
                    case '\t':
                        visibleChar.append(TAB_SIGN);
                        break;
                    case '\r':
                        visibleChar.append(CARRIAGE_RETURN_SIGN);
                        if (textOffset >= length - 1 || text.charAt(textOffset + 1) != '\n') {
                            eol = true;
                            break;
                        }
                        continue;
                    case '\n':
                        visibleChar.append(LINE_FEED_SIGN);
                        break;
                    default:
                        delta = 0;
                        break;
                    }
                }

                if (!eol && visibleChar.length() > 0) {
                    int widgetOffset = 0 + textOffset - visibleChar.length() + delta;
                    fg = Display.getCurrent().getSystemColor(SWT.COLOR_GRAY);
                    draw(gc, widgetOffset, visibleChar.toString(), fg);

                }
                visibleChar.delete(0, visibleChar.length());
            }
        }
    }

    private void draw(GC gc, int offset, String s, Color fg) {

        int baseline = scriptingText.getBaseline(offset);
        FontMetrics fontMetrics = gc.getFontMetrics();
        int fontBaseline = fontMetrics.getAscent() + fontMetrics.getLeading();
        int baslineDelta = baseline - fontBaseline;

        Point pos = scriptingText.getLocationAtOffset(offset);
        gc.setForeground(fg);

        gc.drawString(s, pos.x, pos.y + baslineDelta, true);

    }

    /**
     * Is called when Check Button 'Show Whitespace' is clicked, as the Listener is not called in
     * this case a new instance of GC has to be created.
     * 
     */

    public void drawStyledText() {

        GC gc = new GC(scriptingText);
        gc.setAdvanced(false);

        handleDrawRequest(gc);

    }

    /**
     * Redraws the script without showing whitespace.
     * 
     */
    public void redrawAll() {

        scriptingText.redraw();

    }

    @Override
    public void paintControl(PaintEvent event) {
        handleDrawRequest(event.gc);

    }

    public void setScriptingText(StyledText scriptingText) {
        this.scriptingText = scriptingText;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

}
