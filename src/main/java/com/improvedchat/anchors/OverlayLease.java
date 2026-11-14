package com.improvedchat.anchors;

import java.awt.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

public final class OverlayLease
{
    private static final java.util.Set<OverlayLease> ACTIVE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Overlay overlay;
    private final Point location;
    private final OverlayPosition position;
    private final OverlayPosition originalPosition;
    private final java.util.function.BiConsumer<Boolean,Boolean> mobility;
    private final boolean movable, snappable;
    private final net.runelite.client.ui.overlay.OverlayManager routes;
    private volatile boolean invalidated;
    public OverlayLease(Overlay overlay)
    { this(overlay,null,null); }
    public OverlayLease(Overlay overlay,java.util.function.BiConsumer<Boolean,Boolean> mobility)
    {this(overlay,mobility,null);}
    public OverlayLease(Overlay overlay,java.util.function.BiConsumer<Boolean,Boolean> mobility,net.runelite.client.ui.overlay.OverlayManager routes)
    {
        this.overlay=overlay;
        this.routes=routes;
        this.mobility=mobility;originalPosition=overlay.getPosition();
        location=overlay.getPreferredLocation()==null?null:new Point(overlay.getPreferredLocation());
        position=overlay.getPreferredPosition();movable=overlay.isMovable();snappable=overlay.isSnappable();
        ACTIVE.add(this);hold();
    }
    public void hold()
    {
        if(invalidated)return;
        boolean changed=overlay.isMovable() || overlay.getPosition()!=OverlayPosition.DYNAMIC || overlay.getPreferredPosition()!=OverlayPosition.DYNAMIC || overlay.getPreferredLocation()!=null;
        overlay.setPosition(OverlayPosition.DYNAMIC);overlay.setPreferredPosition(OverlayPosition.DYNAMIC);overlay.setPreferredLocation(null);
        if(changed)refreshRoutes(routes);
    }
    public void move(Point point) { hold();overlay.getBounds().setLocation(point); }
    public void release(boolean keep)
    { release(keep,null); }
    public void release(boolean keep,net.runelite.client.ui.overlay.OverlayManager manager)
    {
        ACTIVE.remove(this);if(invalidated)return;
        Point current=new Point(overlay.getBounds().getLocation());
        java.awt.Dimension size=overlay.getPreferredSize();
        if(keep && manager!=null) {manager.resetOverlay(overlay);if(size!=null)overlay.setPreferredSize(size);}
        overlay.setPosition(originalPosition);
        if(mobility!=null)mobility.accept(movable,snappable);
        overlay.setPreferredLocation(keep && manager!=null?current:location);
        overlay.setPreferredPosition(keep?null:position);
        refreshRoutes(routes);
    }
    public static void beforeProfileChange()
    {
        for(OverlayLease lease:ACTIVE.toArray(new OverlayLease[0])) {lease.release(false);lease.invalidated=true;}
    }
    private static void refreshRoutes(net.runelite.client.ui.overlay.OverlayManager manager)
    {
        if(manager==null)return;
        Overlay marker=new RouteMarker();manager.add(marker);manager.remove(marker);
    }
    private static final class RouteMarker extends Overlay
    {
        RouteMarker(){setPosition(OverlayPosition.DYNAMIC);setLayer(net.runelite.client.ui.overlay.OverlayLayer.MANUAL);}
        @Override public String getName(){return "Interface Layout anchor routing";}
        @Override public java.awt.Dimension render(java.awt.Graphics2D graphics){return null;}
    }
}
