package ru.white.module.impl.render.lootview;

/** Позиции игл силуэта предмета: xs/zs в локальных координатах, jitter — высота, color — доминирующий цвет. */
public record LootShape(float[] xs, float[] zs, float[] jitter, int color, int count) {
}
