package com.example.addon.modules;

import com.example.addon.TuraAddon;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import net.minecraft.client.network.PlayerListEntry;
import static meteordevelopment.meteorclient.MeteorClient.mc; // Sorry for paste )

public class Watermark extends HudElement {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private static int ping() {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return playerListEntry != null ? playerListEntry.getLatency() : 0;
    }
    public static final HudElementInfo<Watermark> INFO = new HudElementInfo<Watermark>(TuraAddon.HUD_GROUP, "Watermark", "Watermark", Watermark::new);

    public Watermark() {
        super(INFO);
    }

    private final Setting<SettingColor> lineColor = sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder())
        .name("line color"))
        .description("Color"))
        .defaultValue(new SettingColor())
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder())
        .name("text color"))
        .description(" Text Color"))
        .defaultValue(new SettingColor())
        .build()
    );

    @Override
    public void render(HudRenderer renderer) {
        int fps = MinecraftClientAccessor.meteor$getFps();
        int tps = (int) TickRate.INSTANCE.getTickRate();
        int ping = ping();

        String text = String.format("TuraAddon | Beta / %d ФПС / %d ТПС / %d МС", fps, tps, ping);
        int textWidth = (int) renderer.textWidth(text, true);
        int textHeight = (int) renderer.textHeight(true);

        int paddingX = 6;
        int paddingY = 3;
        int underlineHeight = 2;
        Color lcolor = lineColor.get();
        Color tcolor = textColor.get();

        int outerWidth = textWidth + paddingX * 2;
        int outerHeight = textHeight + paddingY * 2;

        setSize(outerWidth, outerHeight);

//        renderer.quad(x - backgroundOffset, y - backgroundOffset, outerWidth + backgroundOffset * 2, outerHeight + backgroundOffset * 2, new Color(50, 50, 50, 255));
        renderer.quad(x, y, outerWidth, outerHeight, new Color(0, 0, 0, 255));
        renderer.quad(x, y - underlineHeight, outerWidth, underlineHeight, lcolor);
        renderer.text(text, x + paddingX, y + paddingY, tcolor, true);
        renderer.text("TuraAddon", x + paddingX, y + paddingY, tcolor, true);
    }

}
