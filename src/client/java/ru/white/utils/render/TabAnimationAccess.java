package ru.white.utils.render;

/**
 * Доступ к состоянию анимации таба для InGameHudMixin.
 * Нужен, чтобы дорисовывать список игроков, пока он плавно схлопывается.
 */
public interface TabAnimationAccess {

    boolean nightix$shouldRenderClosingTab();
}