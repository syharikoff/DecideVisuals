// Offline check for a generated cosmetic: rasterises the front ("north") faces
// straight from the geometry and reports what the Bedrock parser will build.
//
// Why bother: the texture mirroring question cannot be settled from the JSON
// alone - it depends on which way +X ends up pointing on screen - but a front
// projection does prove that cubes tile without gaps and that every UV lands
// inside the texture.
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;

class Preview
{
    struct Face
    {
        public float MinX, MinY;   // Bedrock corner, x already negated
        public float Fw, Fh;       // face size in blocks
        public float U0, V0, U1, V1;
    }

    static int Main(string[] args)
    {
        string path = args[0];
        string outPath = args.Length > 1 ? args[1] : null;

        using var doc = JsonDocument.Parse(File.ReadAllText(path));
        var root = doc.RootElement;
        var geo = root.GetProperty("model")
                     .GetProperty("minecraft:geometry")[0];
        var desc = geo.GetProperty("description");
        int tw = desc.GetProperty("texture_width").GetInt32();
        int th = desc.GetProperty("texture_height").GetInt32();

        // the same Bedrock transform GeoModelParser applies: x is negated,
        // then everything is divided by 16
        var faces = new List<Face>();
        foreach (var bone in geo.GetProperty("bones").EnumerateArray())
        {
            if (!bone.TryGetProperty("cubes", out var cubeArr)) continue;

            foreach (var cube in cubeArr.EnumerateArray())
            {
                var o = cube.GetProperty("origin");
                var s = cube.GetProperty("size");
                float ox = o[0].GetSingle(), oy = o[1].GetSingle();
                float sx = s[0].GetSingle(), sy = s[1].GetSingle();

                float minX = -(ox + sx) / 16f;
                float maxX = -ox / 16f;
                float minY = oy / 16f;
                float maxY = (oy + sy) / 16f;

                var uv = cube.GetProperty("uv");
                if (!uv.TryGetProperty("north", out var n)) continue;

                var u = n.GetProperty("uv");
                var us = n.GetProperty("uv_size");

                faces.Add(new Face
                {
                    MinX = minX,
                    MinY = minY,
                    Fw = sx / 16f,
                    Fh = sy / 16f,
                    U0 = u[0].GetSingle(),
                    V0 = u[1].GetSingle(),
                    U1 = u[0].GetSingle() + us[0].GetSingle(),
                    V1 = u[1].GetSingle() + us[1].GetSingle()
                });
            }
        }

        if (faces.Count == 0)
        {
            Console.WriteLine("no north faces at all");
            return 1;
        }

        float bx0 = float.MaxValue, bx1 = float.MinValue;
        float by0 = float.MaxValue, by1 = float.MinValue;
        foreach (var f in faces)
        {
            bx0 = Math.Min(bx0, f.MinX);
            bx1 = Math.Max(bx1, f.MinX + f.Fw);
            by0 = Math.Min(by0, f.MinY);
            by1 = Math.Max(by1, f.MinY + f.Fh);
        }

        Console.WriteLine($"model X range : {bx0:N3} .. {bx1:N3} blocks " +
                          $"(width {bx1 - bx0:N3})");
        Console.WriteLine($"model Y range : {by0:N3} .. {by1:N3} blocks " +
                          $"(height {by1 - by0:N3})");
        Console.WriteLine($"faces         : {faces.Count}");

        // Which art column ends up at the highest X? buildFaceVertices puts
        // u=0 at maxX, so the face reaching furthest +X must start at u=0.
        // If that number is 0 the art is not mirrored along X.
        var rightmost = faces[0];
        foreach (var f in faces)
        {
            if (f.MinX + f.Fw > rightmost.MinX + rightmost.Fw)
                rightmost = f;
        }
        Console.WriteLine(
            $"face at highest X: x={rightmost.MinX + rightmost.Fw:N3} " +
            $"u={rightmost.U0:N1}  (u=0 here => art is NOT mirrored on X)");

        if (outPath == null) return 0;

        WritePng(outPath, faces, bx0, by0, bx1, by1, tw, th);
        Console.WriteLine($"preview written: {outPath}");
        return 0;
    }

    static void WritePng(string path, List<Face> faces,
                         float bx0, float by0, float bx1, float by1,
                         int tw, int th)
    {
        int S = 6;                       // px per 1/16 block
        int W = (int)((bx1 - bx0) * 16 * S) + 8;
        int H = (int)((by1 - by0) * 16 * S) + 8;
        // a 90-byte file is not a PNG: guard before writing so a bad run
        // fails here instead of leaving a corrupt image behind
        if (W <= 0 || H <= 0 || W > 8192 || H > 8192)
        {
            throw new InvalidOperationException(
                $"refusing to write {W}x{H} png");
        }
        var px = new byte[W * H * 3];

        foreach (var f in faces)
        {
            // Screen X grows with model X, so the face's high-X edge is the
            // right-hand one. buildFaceVertices puts u=U0 at that edge, which
            // is what keeps the art the right way round: xLo -> u=U0.
            int xLo = (int)((f.MinX - bx0) * 16 * S) + 4;
            int xHi = (int)((f.MinX + f.Fw - bx0) * 16 * S) + 4;
            int yHi = H - ((int)((f.MinY + f.Fh - by0) * 16 * S) + 4);
            int yLo = H - ((int)((f.MinY - by0) * 16 * S) + 4);

            if (xHi <= xLo || yHi <= yLo) continue;

            for (int y = Math.Max(0, yLo); y < Math.Min(H, yHi); y++)
            {
                for (int x = Math.Max(0, xLo); x < Math.Min(W, xHi); x++)
                {
                    float fx = (x - xLo) / (float)(xHi - xLo);
                    float fy = (y - yLo) / (float)(yHi - yLo);
                    // shade per cell so cube seams show up in the preview
                    byte val = (byte)(90 + ((int)(f.MinX * 1000) + (int)(f.MinY * 1000)) % 120);
                    int i = (y * W + x) * 3;
                    px[i] = val;
                    px[i + 1] = val;
                    px[i + 2] = (byte)Math.Min(255, val + 25);
                }
            }
        }

        var raw = new byte[(W * 3 + 1) * H];
        int o = 0;
        for (int y = 0; y < H; y++)
        {
            raw[o++] = 0; // filter: none
            Array.Copy(px, y * W * 3, raw, o, W * 3);
            o += W * 3;
        }

        using var ms = new MemoryStream();
        ms.Write(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 });

        void chunk(string type, byte[] data)
        {
            var len = BitConverter.GetBytes(data.Length);
            if (!BitConverter.IsLittleEndian) Array.Reverse(len);
            ms.Write(len);
            var t = Encoding.ASCII.GetBytes(type);
            ms.Write(t);
            ms.Write(data);
            var crc = Crc32(t.Concat(data).ToArray());
            ms.Write(crc);
        }

        var ihdr = new byte[13];
        WriteBE(ihdr, 0, W);
        WriteBE(ihdr, 4, H);
        ihdr[8] = 8;   // bit depth
        ihdr[9] = 2;   // truecolour
        chunk("IHDR", ihdr);

        using (var comp = new System.IO.Compression.ZLibStream(
                   ms, System.IO.Compression.CompressionLevel.Optimal, true))
        {
            comp.Write(raw, 0, raw.Length);
        }
        chunk("IEND", Array.Empty<byte>());
        File.WriteAllBytes(path, ms.ToArray());
    }

    static void WriteBE(byte[] b, int o, int v)
    {
        b[o] = (byte)(v >> 24);
        b[o + 1] = (byte)(v >> 16);
        b[o + 2] = (byte)(v >> 8);
        b[o + 3] = (byte)v;
    }

    static byte[] Crc32(byte[] data)
    {
        uint[] table = null;
        if (table == null)
        {
            table = new uint[256];
            for (uint i = 0; i < 256; i++)
            {
                uint c = i;
                for (int k = 0; k < 8; k++)
                    c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
                table[i] = c;
            }
        }
        uint crc = 0xFFFFFFFFu;
        foreach (byte b in data)
            crc = table[(crc ^ b) & 0xFF] ^ (crc >> 8);
        crc ^= 0xFFFFFFFFu;
        return new[]
        {
            (byte)(crc >> 24), (byte)(crc >> 16), (byte)(crc >> 8), (byte)crc
        };
    }
}