package com.improvedchat.anchors;

import com.improvedchat.ImprovedChatConfig;
import com.improvedchat.ImprovedChatPlugin;
import com.improvedchat.input.ChatInputDock;
import com.improvedchat.input.ChatInputDockOverlay;
import com.improvedchat.input.InputDockPlacement;
import com.improvedchat.overlay.DynamicChatOverlay;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

@Singleton
public final class ChatAnchorAdapter implements AnchorParticipant.Provider
{
    @Inject private ImprovedChatPlugin plugin;
    @Inject private ImprovedChatConfig config;
    @Inject private ChatInputDockOverlay dock;
    @Inject private ChatInputDock input;
    @Inject private Client client;
    @Inject private ClientThread thread;
    @Inject private EventBus events;
    @Inject private OverlayManager overlays;
    private final Map<Overlay,Target> targets=new java.util.concurrent.ConcurrentHashMap<>();
    private AnchorParticipant participant;
    private final java.awt.image.BufferedImage metrics=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    public void start() {participant=new AnchorParticipant("Improved Chat",events,thread,this);participant.start();}
    public void stop() {if(participant!=null)participant.stop();participant=null;}
    public boolean owns(Overlay overlay) {Target target=targets.get(overlay);return target!=null && target.lease!=null;}
    @Override public Collection<AnchorParticipant.Target> targets()
    {
        List<Overlay> live=new ArrayList<>(plugin.anchorOverlays());live.add(dock);
        targets.keySet().removeIf(overlay -> !live.contains(overlay) && targets.get(overlay).lease==null);
        List<AnchorParticipant.Target> result=new ArrayList<>();
        for(Overlay overlay:live){Target target=targets.computeIfAbsent(overlay,Target::new);target.measure=true;result.add(target);}
        return result;
    }
    private final class Target implements AnchorParticipant.Target
    {
        private final Overlay overlay;
        private OverlayLease lease;
        private boolean measure=true;
        private java.awt.Dimension footprint=new java.awt.Dimension();
        Target(Overlay overlay) {this.overlay=overlay;}
        public String id() {return overlay==dock?"chat.input-dock":"chat.history:"+((DynamicChatOverlay)overlay).getOverlayConfig().getId();}
        public String name() {return overlay==dock?"Chat input dock":((DynamicChatOverlay)overlay).getOverlayConfig().getName();}
        public Rectangle bounds() {
            if(!measure)return new Rectangle(overlay.getBounds().getLocation(),footprint);
            Rectangle result=measureBounds();footprint=result.getSize();measure=false;return result;
        }
        private Rectangle measureBounds() {
            if(overlay instanceof DynamicChatOverlay)return ((DynamicChatOverlay)overlay).anchorBounds();
            Rectangle result=new Rectangle(overlay.getBounds());java.awt.Graphics2D graphics=metrics.createGraphics();
            try {com.improvedchat.input.InputDockLayout layout=input.layout(graphics,false);if(layout!=null)result.setSize(layout.size);}
            finally {graphics.dispose();}
            if(result.isEmpty())result.setSize(160*config.inputDockScale()/100,24*config.inputDockScale()/100);return result;
        }
        public String state() {
            if(overlay instanceof DynamicChatOverlay)return ((DynamicChatOverlay)overlay).anchorState();
            if(!config.inputDockEnabled())return "HIDDEN";
            if(config.inputDockPlacement()!=InputDockPlacement.FLOATING || client.getGameState()!=GameState.LOGGED_IN)return "UNAVAILABLE";
            return input.canSwitch()?"VISIBLE":"OCCLUDED";
        }
        public boolean available() {return state().equals("VISIBLE") || state().equals("OCCLUDED");}
        public List<String> aliases() {return List.of(overlay.getName());}
        public boolean claim() {if(lease==null)lease=new OverlayLease(overlay,null,overlays);return available();}
        public void move(Point point) {lease.move(point);}
        public void release(boolean keep) {if(lease!=null){lease.release(keep,overlays);lease=null;if(overlay instanceof DynamicChatOverlay)((DynamicChatOverlay)overlay).anchorReleasePolicy();if(keep)overlays.saveOverlay(overlay);}}
    }
}
