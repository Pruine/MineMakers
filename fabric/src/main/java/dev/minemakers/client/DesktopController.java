package dev.minemakers.client;

import dev.minemakers.bridge.*;
import dev.minemakers.mixin.MouseInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Reuses vanilla key bindings, screens, slot clicks and mouse button handling. */
public final class DesktopController {
    private static final Set<Integer> held=new HashSet<>();
    private static String session="";
    private static long lastEvent,lastSequence=-1;
    private static double totalX,totalY;
    private static boolean active;
    private static final Set<Integer> buttons=new HashSet<>();
    private static double cursorX=0.5,cursorY=0.5;
    public static boolean isHeld(int key) { return held.contains(key); }
    public static long ack() { return lastEvent; }
    public static String session() { return session; }
    public static boolean active() { return active; }
    public static void release() {
        for (int key:held) KeyBinding.setKeyPressed(InputUtil.fromKeyCode(key,0),false);
        held.clear();
        MinecraftClient mc=MinecraftClient.getInstance();
        if (active && mc.player!=null) {
            MouseInvoker mouse=(MouseInvoker)mc.mouse;
            for (int b=0;b<3;b++) mouse.minemakers$button(mc.getWindow().getHandle(),b,GLFW.GLFW_RELEASE,0);
        }
        buttons.clear();
        active=false;
    }
    public static boolean tick(MinecraftClient mc,Bridge bridge) {
        Bridge.HostFrame frame=bridge==null ? null : bridge.desktop();
        DesktopInput input=null;
        try { if (frame!=null) input=DesktopInput.parse(frame.wire()); } catch (IllegalArgumentException ignored) { }
        if (input==null || !input.active() || mc.player==null || mc.isWindowFocused()) { if (active) release(); return false; }
        boolean freshSession=!session.equals(input.session());
        if (freshSession) { release(); session=input.session(); lastEvent=0; lastSequence=-1; totalX=0; totalY=0; }
        active=true;
        if (input.sequence()==lastSequence) return true;
        lastSequence=input.sequence();
        for (int key:new HashSet<>(held)) if (!input.held().contains(key)) KeyBinding.setKeyPressed(InputUtil.fromKeyCode(key,0),false);
        held.clear(); held.addAll(input.held());
        MouseInvoker mouse=(MouseInvoker)mc.mouse;
        double x=input.cursorX()*mc.getWindow().getWidth(),y=input.cursorY()*mc.getWindow().getHeight();
        mouse.minemakers$cursor(mc.getWindow().getHandle(),x,y);
        if (mc.currentScreen==null) {
            double sensitivity=mc.options.getMouseSensitivity().getValue()*0.6+0.2;
            double scale=sensitivity*sensitivity*sensitivity*8;
            double dx=Math.clamp(input.totalX()-totalX,-2000,2000),dy=Math.clamp(input.totalY()-totalY,-2000,2000);
            mc.player.changeLookDirection(dx*scale,dy*scale*(mc.options.getInvertYMouse().getValue() ? -1 : 1));
        } else mc.currentScreen.mouseMoved(input.cursorX()*mc.getWindow().getScaledWidth(),input.cursorY()*mc.getWindow().getScaledHeight());
        totalX=input.totalX(); totalY=input.totalY();
        for (DesktopInput.Event e:input.events()) {
            if (e.id()<=lastEvent) continue;
            lastEvent=e.id(); // at most once, even if a vanilla handler changes screen
            var a=e.args();
            switch(e.type()) {
                case "KEY" -> {
                    int key=Integer.parseInt(a.get(0)),action=Integer.parseInt(a.get(1)),mods=Integer.parseInt(a.get(2));
                    if (key<0 || key>348 || action<0 || action>2) break;
                    if (mc.currentScreen!=null) {
                        if (action==GLFW.GLFW_RELEASE) mc.currentScreen.keyReleased(key,0,mods);
                        else mc.currentScreen.keyPressed(key,0,mods);
                    } else if (action!=GLFW.GLFW_RELEASE && key==GLFW.GLFW_KEY_ESCAPE) mc.openGameMenu(false);
                    else {
                        InputUtil.Key binding=InputUtil.fromKeyCode(key,0);
                        KeyBinding.setKeyPressed(binding,action!=GLFW.GLFW_RELEASE);
                        if (action==GLFW.GLFW_PRESS) KeyBinding.onKeyPressed(binding);
                    }
                }
                case "CHAR" -> {
                    int code=Integer.parseInt(a.get(0));
                    if (mc.currentScreen!=null && code>=32 && code<=65535) mc.currentScreen.charTyped((char)code,0);
                }
                case "BUTTON" -> {
                    int button=Integer.parseInt(a.get(0)),action=Integer.parseInt(a.get(1)),mods=Integer.parseInt(a.get(2));
                    double bx=Wire.number(a.get(3)),by=Wire.number(a.get(4));
                    if (button<0 || button>2 || action<0 || action>1 || bx<0 || bx>1 || by<0 || by>1) break;
                    mouse.minemakers$cursor(mc.getWindow().getHandle(),bx*mc.getWindow().getWidth(),by*mc.getWindow().getHeight());
                    mouse.minemakers$button(mc.getWindow().getHandle(),button,action,mods);
                    if (action==GLFW.GLFW_PRESS) buttons.add(button); else buttons.remove(button);
                }
                case "SCROLL" -> mouse.minemakers$scroll(mc.getWindow().getHandle(),0,Math.clamp(Wire.number(a.get(0)),-20,20));
            }
        }
        if (mc.currentScreen!=null) for (int button:buttons) mc.currentScreen.mouseDragged(input.cursorX()*mc.getWindow().getScaledWidth(),input.cursorY()*mc.getWindow().getScaledHeight(),button,(input.cursorX()-cursorX)*mc.getWindow().getScaledWidth(),(input.cursorY()-cursorY)*mc.getWindow().getScaledHeight());
        cursorX=input.cursorX(); cursorY=input.cursorY();
        if (mc.currentScreen==null) for (int key:held) KeyBinding.setKeyPressed(InputUtil.fromKeyCode(key,0),true);
        if (mc.currentScreen!=null && mc.mouse.isCursorLocked()) mc.mouse.unlockCursor();
        return true;
    }
}
