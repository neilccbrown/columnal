package utility;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.RecordSet;
import records.data.TextFileNumericColumn;
import records.data.TextFileStringColumn;
import records.error.FetchException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by neil on 20/10/2016.
 */
public class Import
{

    public static final int MAX_HEADER_ROWS = 20;
    public static final int INITIAL_ROWS_TEXT_FILE = 100;

    public static enum ColumnType
    {
        NUMERIC, TEXT, BLANK;
    }

    public static class ColumnInfo
    {
        public final ColumnType type;
        public final String title;
        public final String displayPrefix;

        public ColumnInfo(ColumnType type, String title)
        {
            this(type, title, "");
        }

        public ColumnInfo(ColumnType type, String title, String displayPrefix)
        {
            this.type = type;
            this.title = title;
            this.displayPrefix = displayPrefix;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ColumnInfo that = (ColumnInfo) o;

            if (type != that.type) return false;
            return title.equals(that.title) && displayPrefix.equals(that.displayPrefix);

        }

        @Override
        public int hashCode()
        {
            int result = type.hashCode();
            result = 31 * result + title.hashCode();
            result = 31 * result + displayPrefix.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "ColumnInfo{" +
                "type=" + type +
                ", title='" + title + '\'' +
                ", displayPrefix='" + displayPrefix + '\'' +
                '}';
        }
    }

    public static class Format
    {
        public final int headerRows;
        public final List<ColumnInfo> columnTypes;
        public final List<String> problems = new ArrayList<>();

        public Format(int headerRows, List<ColumnInfo> columnTypes)
        {
            this.headerRows = headerRows;
            this.columnTypes = columnTypes;
        }

        public Format(Format copyFrom)
        {
            this(copyFrom.headerRows, copyFrom.columnTypes);
        }

        public void recordProblem(String problem)
        {
            problems.add(problem);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Format format = (Format) o;

            if (headerRows != format.headerRows) return false;
            return columnTypes.equals(format.columnTypes);

        }

        @Override
        public int hashCode()
        {
            int result = headerRows;
            result = 31 * result + columnTypes.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "Format{" +
                "headerRows=" + headerRows +
                ", columnTypes=" + columnTypes +
                '}';
        }
    }

    public static class TextFormat extends Format
    {
        public char separator;

        public TextFormat(Format copyFrom, char separator)
        {
            super(copyFrom);
            this.separator = separator;
        }

        public TextFormat(int headerRows, List<ColumnInfo> columnTypes, char separator)
        {
            super(headerRows, columnTypes);
            this.separator = separator;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            TextFormat that = (TextFormat) o;

            return separator == that.separator;

        }

        @Override
        public int hashCode()
        {
            int result = super.hashCode();
            result = 31 * result + (int) separator;
            return result;
        }

        @Override
        public String toString()
        {
            return "TextFormat{" +
                "headerRows=" + headerRows +
                ", columnTypes=" + columnTypes +
                ", separator=" + separator +
                '}';
        }
    }

    public static class GuessException extends Exception
    {
        public GuessException(String message)
        {
            super(message);
        }
    }

    @OnThread(Tag.Simulation)
    public static void importTextFile(File textFile, Consumer<RecordSet> afterLoaded) throws IOException
    {
        // Read the first few lines:
        try (BufferedReader br = new BufferedReader(new FileReader(textFile))) {
            String line;
            List<String> initial = new ArrayList<>();
            while ((line = br.readLine()) != null && initial.size() < INITIAL_ROWS_TEXT_FILE) {
                initial.add(line);
            }
            TextFormat format = guessTextFormat(initial);

            long startPosition = Utility.skipFirstNRows(textFile, format.headerRows).startFrom;

            List<Function<RecordSet, Column>> columns = new ArrayList<>();
            for (int i = 0; i < format.columnTypes.size(); i++)
            {
                ColumnInfo columnInfo = format.columnTypes.get(i);
                int iFinal = i;
                switch (columnInfo.type)
                {
                    case NUMERIC:
                        columns.add(rs -> new TextFileNumericColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal));
                        break;
                    case TEXT:
                        columns.add(rs -> new TextFileStringColumn(rs, textFile, startPosition, (byte) format.separator, columnInfo.title, iFinal));
                        break;
                    // If it's blank, should we add any column?
                    // Maybe if it has title?
                }
            }



            afterLoaded.accept(new RecordSet(textFile.getName(), columns) {
                    protected int rowCount = -1;

                    @Override
                    public final boolean indexValid(int index) throws UserException
                    {
                        return index < getLength();
                    }

                    @Override
                    public int getLength() throws UserException
                    {
                        if (rowCount == -1)
                        {
                            try
                            {
                                rowCount = Utility.countLines(textFile) - format.headerRows;
                            }
                            catch (IOException e)
                            {
                                throw new FetchException("Error counting rows", e);
                            }
                        }
                        return rowCount;
                    }
                });

        }
    }

    public static TextFormat guessTextFormat(List<String> initial)
    {
        try
        {
            // All-text formats, indexed by number of header rows:
            final TreeMap<Integer, TextFormat> allText = new TreeMap<>();
            // We advance the number of header rows until everything makes sense:
            for (int headerRows = 0; headerRows < Math.min(MAX_HEADER_ROWS, initial.size() - 1); headerRows++)
            {

                Map<String, Double> sepScores = new HashMap<>();
                // Guess the separator:
                // Earlier in this list is most preferred:
                List<String> seps = Arrays.asList(";", ",", "\t", ":", " ");
                for (String sep : seps)
                {
                    List<Integer> counts = new ArrayList<>(initial.size() - headerRows);
                    for (int i = headerRows; i < initial.size(); i++)
                        counts.add(Utility.countIn(sep, initial.get(i)));
                    if (counts.stream().allMatch(c -> c.intValue() == 0))
                    {
                        // None found; so rubbish we shouldn't record
                    } else
                    {
                        sepScores.put(sep, Utility.variance(counts));
                    }
                }

                if (sepScores.isEmpty())
                    continue;

                Entry<String, Double> sep = sepScores.entrySet().stream().min(Comparator.<Entry<String, Double>, Double>comparing(e -> e.getValue()).thenComparing(e -> seps.indexOf(e.getKey()))).get();

                if (sep.getValue().doubleValue() == 0.0)
                {
                    // Spot on!  Read first line after headers to get column count
                    int columnCount = initial.get(headerRows).split(sep.getKey()).length;

                    List<@NonNull List<@NonNull String>> initialVals = Utility.<@NonNull String, @NonNull List<@NonNull String>>mapList(initial, s -> Arrays.asList(s.split(sep.getKey())));

                    //List<Function<RecordSet, Column>> columns = new ArrayList<>();
                    Format format = guessFormat(columnCount, headerRows, initialVals);
                    TextFormat textFormat = new TextFormat(format, sep.getKey().charAt(0));
                    // If they are all text record this as feasible but keep going in case we get better
                    // result with more header rows:
                    if (format.columnTypes.stream().allMatch(c -> c.type == ColumnType.TEXT || c.type == ColumnType.BLANK))
                        allText.put(headerRows, textFormat);
                    else // Not all just text; go with it:
                        return textFormat;

                }
            }
            Entry<Integer, TextFormat> firstAllText = allText.firstEntry();
            if (firstAllText != null)
                return firstAllText.getValue();
            else
                throw new GuessException("Couldn't guess column separator");
        }
        catch (GuessException e)
        {
            // Always valid backup: a single text column, no header
            TextFormat fmt = new TextFormat(new Format(0, Collections.singletonList(new ColumnInfo(ColumnType.TEXT, ""))), (char)-1);
            String msg = e.getLocalizedMessage();
            fmt.recordProblem(msg == null ? "Unknown" : msg);
            return fmt;
        }
    }

    private static Format guessFormat(int columnCount, int headerRows, @NonNull List<@NonNull List<@NonNull String>> initialVals)
    {
        // Per row, for how many columns is it viable to get column name?
        Map<Integer, Integer> viableColumnNameRows = new HashMap<>();
        List<ColumnType> columnTypes = new ArrayList<>();
        List<String> columnPrefixes = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            // Have a guess at column type:
            boolean allNumeric = true;
            boolean allBlank = true;
            String commonPrefix = "";
            for (int rowIndex = headerRows; rowIndex < initialVals.size(); rowIndex++)
            {
                List<String> row = initialVals.get(rowIndex);
                if (!row.isEmpty())
                {

                    try
                    {
                        String val = row.get(columnIndex).trim();
                        if (!val.isEmpty())
                            allBlank = false;
                        if (commonPrefix.isEmpty())
                        {
                            // Look for a prefix of currency symbol:
                            for (int i = 0; i < val.length(); i = val.offsetByCodePoints(i, 1))
                            {
                                if (Character.getType(val.codePointAt(i)) == Character.CURRENCY_SYMBOL)
                                    commonPrefix += val.substring(i, val.offsetByCodePoints(i, 1));
                                else
                                    break;
                            }
                        }
                        // Not an else; if we just picked commonPrefix, we should find it here:
                        if (!commonPrefix.isEmpty() && val.startsWith(commonPrefix))
                        {
                            // Take off prefix and continue as is:
                            val = val.substring(commonPrefix.length()).trim();
                        }
                        else if (!commonPrefix.isEmpty())
                        {
                            // We thought we had a prefix, but we haven't found it here, so give up:
                            commonPrefix = "";
                            allNumeric = false;
                            break;
                        }
                        new BigDecimal(val);
                    }
                    catch (NumberFormatException e)
                    {
                        allNumeric = false;
                        commonPrefix = "";
                    }
                }
            }
            columnTypes.add(allBlank ? ColumnType.BLANK : (allNumeric ? ColumnType.NUMERIC : ColumnType.TEXT));
            columnPrefixes.add(commonPrefix);
            // Go backwards to find column titles:

            for (int headerRow = headerRows - 1; headerRow >= 0; headerRow--)
            {
                // Must actually have our column in it:
                if (columnIndex < initialVals.get(headerRow).size())
                {
                    viableColumnNameRows.compute(headerRow, (a, pre) -> pre == null ? 1 : (1 + pre));
                }
            }
        }
        // All must think it's viable, and then pick last one:
        Optional<List<String>> headerRow = viableColumnNameRows.entrySet().stream().filter(e -> e.getValue() == columnCount).max(Entry.comparingByKey()).map(e -> initialVals.get(e.getKey()));

        List<ColumnInfo> columns = new ArrayList<>(columnCount);
        for (int columnIndex = 0; columnIndex < columnTypes.size(); columnIndex++)
            columns.add(new ColumnInfo(columnTypes.get(columnIndex), headerRow.isPresent() ? headerRow.get().get(columnIndex) : "", columnPrefixes.get(columnIndex)));
        return new Format(headerRows, columns);
    }
}
