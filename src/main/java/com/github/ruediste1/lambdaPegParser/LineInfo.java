package com.github.ruediste1.lambdaPegParser;

/**
 * Information about the line a position is located in a string.
 */
public class LineInfo {

    /**
     * Line number the error occured in. First line has count 1
     */
    private int lineNr;
    /**
     * Input line the error occured in
     */
    private String line;

    /**
     * index of the error in the error line
     */
    private int indexInLine;

    public LineInfo(String content, int position) {
        int lineNr = 1;
        int idx = 0;
        while (true) {
            int endIdx = content.indexOf('\n', idx);

            if (position >= idx && (endIdx == -1 || endIdx + 1 > position)) {
                line = content.substring(idx,
                        endIdx == -1 ? content.length() : endIdx);
                indexInLine = position - idx;
                this.lineNr = lineNr;
                break;
            }
            if (endIdx == -1)
                break;
            idx = endIdx + 1;
            lineNr++;
        }
    }

    /**
     * Return a line suitable to underline the error line
     * 
     * @param spacerCP
     *            codePoint of the caracter to use as space
     * @param positionMarkerCP
     *            codePoint of the caracter to use as marker
     */
    public String getUnderline(int spacerCP, int positionMarkerCP) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (; i < indexInLine; i++) {
            sb.appendCodePoint(spacerCP);
        }

        sb.appendCodePoint(positionMarkerCP);
        for (; i < line.length() - 1; i++) {
            sb.appendCodePoint(spacerCP);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "LineInfo: Line: " + lineNr + "\n" + line + "\n"
                + getUnderline(' ', '^');
    }

    public int getLineNr() {
        return lineNr;
    }

    public String getLine() {
        return line;
    }

    public int getIndexInLine() {
        return indexInLine;
    }

}
