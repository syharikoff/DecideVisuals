package ru.white.module.api.settings.impl;

import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Квадратная пиксельная сетка — нарисованный вручную прицел. В конфиг уезжает
 * списком строк из нулей и единиц, по строке на ряд.
 */
public class PixelGridSetting extends Setting<boolean[][]> {

    public final int size;

    public PixelGridSetting(Module parent, String name, int size) {
        super(parent, name, new boolean[size][size]);
        this.size = size;
        reset();
    }

    public int center() {
        return size / 2;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    public boolean get(int x, int y) {
        return inBounds(x, y) && getValue()[y][x];
    }

    public void put(int x, int y, boolean filled) {
        if (inBounds(x, y)) getValue()[y][x] = filled;
    }

    public void clear() {
        for (boolean[] row : getValue()) {
            java.util.Arrays.fill(row, false);
        }
    }

    public boolean isEmpty() {
        for (boolean[] row : getValue()) {
            for (boolean cell : row) {
                if (cell) return false;
            }
        }
        return true;
    }

    /** Классический прицел из четырёх палок — с него начинается рисование. */
    public void reset() {
        clear();

        int c = center();
        int gap = Math.max(1, size / 7);
        int length = Math.max(2, size / 5);

        for (int i = gap; i < gap + length && c + i < size; i++) {
            put(c, c - i, true);
            put(c, c + i, true);
            put(c - i, c, true);
            put(c + i, c, true);
        }
    }

    public List<String> toRows() {
        List<String> rows = new ArrayList<>(size);
        for (boolean[] row : getValue()) {
            StringBuilder sb = new StringBuilder(size);
            for (boolean cell : row) {
                sb.append(cell ? '1' : '0');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    public void fromRows(List<String> rows) {
        clear();
        for (int y = 0; y < size && y < rows.size(); y++) {
            String row = rows.get(y);
            for (int x = 0; x < size && x < row.length(); x++) {
                getValue()[y][x] = row.charAt(x) == '1';
            }
        }
    }

    @Override
    public PixelGridSetting setVisible(Supplier<Boolean> value) {
        return (PixelGridSetting) super.setVisible(value);
    }
}
