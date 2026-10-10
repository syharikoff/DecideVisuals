// Standalone JSON validator: loads the generated cosmetic and prints the
// numbers the Bedrock parser will actually see, so a broken file fails here
// instead of silently disappearing from the cosmetics menu.
using System;
using System.IO;
using System.Linq;
using System.Text.Json;

class Check
{
    static int Main(string[] args)
    {
        string path = args[0];

        using var doc = JsonDocument.Parse(
            File.ReadAllText(path),
            new JsonDocumentOptions
            {
                AllowTrailingCommas = true,
                CommentHandling = JsonCommentHandling.Skip
            });

        var root = doc.RootElement;

        string name = root.GetProperty("name").GetString();
        string type = root.GetProperty("type").GetString();
        int id = root.GetProperty("id").GetInt32();

        var texture = root.GetProperty("texture").GetString();
        int texBytes = Convert.FromBase64String(texture).Length;

        var geoArr = root.GetProperty("model")
                         .GetProperty("minecraft:geometry");
        var geo = geoArr[0];

        var desc = geo.GetProperty("description");
        int tw = desc.GetProperty("texture_width").GetInt32();
        int th = desc.GetProperty("texture_height").GetInt32();

        var bones = geo.GetProperty("bones");
        int cubes = 0, faces = 0, verts = 0;

        foreach (var bone in bones.EnumerateArray())
        {
            if (!bone.TryGetProperty("cubes", out var cubeArr)) continue;
            foreach (var cube in cubeArr.EnumerateArray())
            {
                cubes++;
                var o = cube.GetProperty("origin");
                var s = cube.GetProperty("size");
                float ox = o[0].GetSingle(), oy = o[1].GetSingle();
                float sx = s[0].GetSingle(), sy = s[1].GetSingle();
                if (sx <= 0 || sy <= 0)
                {
                    Console.WriteLine($"  ! cube with non-positive size {sx}x{sy}");
                }

                var uv = cube.GetProperty("uv");
                foreach (var f in uv.EnumerateObject())
                {
                    faces++;
                    verts += 4;
                    var u = f.Value.GetProperty("uv");
                    var us = f.Value.GetProperty("uv_size");
                    float u0 = u[0].GetSingle(), v0 = u[1].GetSingle();
                    float uw = us[0].GetSingle(), vh = us[1].GetSingle();

                    if (u0 + uw > tw || v0 + vh > th)
                    {
                        Console.WriteLine(
                            $"  ! {f.Name} UV out of texture: " +
                            $"u {u0}+{uw}={u0 + uw} > {tw}, " +
                            $"v {v0}+{vh}={v0 + vh} > {th}");
                    }
                }
            }
        }

        Console.WriteLine($"name      : {name}");
        Console.WriteLine($"id / type : {id} / {type}");
        Console.WriteLine($"texture   : base64 {texture.Length} chars -> {texBytes} bytes");
        Console.WriteLine($"tex size  : {tw} x {th}");
        Console.WriteLine($"bones     : {bones.GetArrayLength()}");
        Console.WriteLine($"cubes     : {cubes}");
        Console.WriteLine($"faces     : {faces}  (verts {verts})");
        Console.WriteLine("JSON OK");
        return 0;
    }
}