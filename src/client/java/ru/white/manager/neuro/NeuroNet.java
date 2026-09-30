package ru.white.manager.neuro;

import java.util.Random;

/**
 * Маленькая полносвязная нейросеть (MLP) 2-8-2 с обучением через backprop.
 * Учится по парам (ошибка наведения -> скорость поворота человека), чтобы
 * потом выдавать человекоподобную скорость доводки по текущей ошибке.
 *
 * Вход:  [|yawErr|/180, |pitchErr|/90]
 * Выход: [yawSpeedNorm, pitchSpeedNorm] в 0..1 (масштабируется снаружи)
 */
public class NeuroNet {

    public final int in, hid, out;
    public final double[][] w1; // [hid][in]
    public final double[] b1;   // [hid]
    public final double[][] w2; // [out][hid]
    public final double[] b2;   // [out]

    private static final Random RND = new Random();

    public NeuroNet(int in, int hid, int out) {
        this.in = in;
        this.hid = hid;
        this.out = out;
        w1 = new double[hid][in];
        b1 = new double[hid];
        w2 = new double[out][hid];
        b2 = new double[out];

        double s1 = Math.sqrt(2.0 / in);
        double s2 = Math.sqrt(2.0 / hid);
        for (int j = 0; j < hid; j++)
            for (int i = 0; i < in; i++)
                w1[j][i] = (RND.nextDouble() * 2 - 1) * s1;
        for (int k = 0; k < out; k++)
            for (int j = 0; j < hid; j++)
                w2[k][j] = (RND.nextDouble() * 2 - 1) * s2;
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    public double[] forward(double[] x) {
        double[] h = new double[hid];
        for (int j = 0; j < hid; j++) {
            double s = b1[j];
            for (int i = 0; i < in; i++) s += w1[j][i] * x[i];
            h[j] = Math.tanh(s);
        }
        double[] o = new double[out];
        for (int k = 0; k < out; k++) {
            double s = b2[k];
            for (int j = 0; j < hid; j++) s += w2[k][j] * h[j];
            o[k] = sigmoid(s);
        }
        return o;
    }

    /** Один шаг SGD по одному образцу, возвращает квадрат ошибки. */
    private double train1(double[] x, double[] y, double lr) {
        double[] h = new double[hid];
        for (int j = 0; j < hid; j++) {
            double s = b1[j];
            for (int i = 0; i < in; i++) s += w1[j][i] * x[i];
            h[j] = Math.tanh(s);
        }
        double[] o = new double[out];
        for (int k = 0; k < out; k++) {
            double s = b2[k];
            for (int j = 0; j < hid; j++) s += w2[k][j] * h[j];
            o[k] = sigmoid(s);
        }

        double err = 0;
        double[] dOut = new double[out];
        for (int k = 0; k < out; k++) {
            double e = o[k] - y[k];
            err += e * e;
            dOut[k] = e * o[k] * (1 - o[k]);
        }
        double[] dHid = new double[hid];
        for (int j = 0; j < hid; j++) {
            double s = 0;
            for (int k = 0; k < out; k++) s += dOut[k] * w2[k][j];
            dHid[j] = s * (1 - h[j] * h[j]);
        }

        for (int k = 0; k < out; k++) {
            for (int j = 0; j < hid; j++) w2[k][j] -= lr * dOut[k] * h[j];
            b2[k] -= lr * dOut[k];
        }
        for (int j = 0; j < hid; j++) {
            for (int i = 0; i < in; i++) w1[j][i] -= lr * dHid[j] * x[i];
            b1[j] -= lr * dHid[j];
        }
        return err;
    }

    /** Прогоняет epochs эпох по выборке, возвращает среднюю ошибку последней эпохи. */
    public double train(double[][] X, double[][] Y, int epochs, double lr) {
        double last = 0;
        int n = X.length;
        for (int e = 0; e < epochs; e++) {
            double tot = 0;
            for (int i = 0; i < n; i++) tot += train1(X[i], Y[i], lr);
            last = tot / Math.max(1, n);
        }
        return last;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(in).append(' ').append(hid).append(' ').append(out).append('\n');
        for (double[] r : w1) for (double v : r) sb.append(v).append(' ');
        sb.append('\n');
        for (double v : b1) sb.append(v).append(' ');
        sb.append('\n');
        for (double[] r : w2) for (double v : r) sb.append(v).append(' ');
        sb.append('\n');
        for (double v : b2) sb.append(v).append(' ');
        sb.append('\n');
        return sb.toString();
    }

    public static NeuroNet deserialize(String data) {
        String[] lines = data.split("\n");
        String[] dims = lines[0].trim().split("\\s+");
        int in = Integer.parseInt(dims[0]);
        int hid = Integer.parseInt(dims[1]);
        int out = Integer.parseInt(dims[2]);
        NeuroNet net = new NeuroNet(in, hid, out);

        double[] w1f = parse(lines[1]);
        int idx = 0;
        for (int j = 0; j < hid; j++) for (int i = 0; i < in; i++) net.w1[j][i] = w1f[idx++];
        double[] b1f = parse(lines[2]);
        for (int j = 0; j < hid; j++) net.b1[j] = b1f[j];
        double[] w2f = parse(lines[3]);
        idx = 0;
        for (int k = 0; k < out; k++) for (int j = 0; j < hid; j++) net.w2[k][j] = w2f[idx++];
        double[] b2f = parse(lines[4]);
        for (int k = 0; k < out; k++) net.b2[k] = b2f[k];
        return net;
    }

    private static double[] parse(String line) {
        String[] parts = line.trim().split("\\s+");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
        return v;
    }
}
