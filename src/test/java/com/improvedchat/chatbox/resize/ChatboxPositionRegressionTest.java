package com.improvedchat.chatbox.resize;

import com.improvedchat.ImprovedChatConfig;
import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatboxPositionRegressionTest {
    private static Object zero(Class<?> t) {
        if(t==boolean.class)return false; if(t==int.class)return 0;
        if(t==long.class)return 0L; if(t==double.class)return 0d; return null;
    }
    private static class W {
        final Map<String,Object> data=new HashMap<>(); final Widget widget;
        W(int id, Widget parent, int width, int height) {
            data.put("Id",id);data.put("Parent",parent);data.put("Width",width);data.put("Height",height);
            widget=(Widget)Proxy.newProxyInstance(Widget.class.getClassLoader(),new Class[]{Widget.class},(p,m,a)->{
                String n=m.getName();
                if(n.equals("equals"))return p==a[0]; if(n.equals("hashCode"))return System.identityHashCode(p);
                if(n.equals("setSize")){data.put("Width",a[0]);data.put("Height",a[1]);return widgetResult(m,p);}
                if(n.equals("setForcedPosition")){data.put("Forced",new Point((int)a[0],(int)a[1]));return null;}
                if(n.startsWith("get"))return data.getOrDefault(n.substring(3),zero(m.getReturnType()));
                if(n.startsWith("is"))return data.getOrDefault(n.substring(2),zero(m.getReturnType()));
                if(n.startsWith("set")){data.put(n.substring(3),a[0]);return widgetResult(m,p);}
                return zero(m.getReturnType());
            });
        }
        private Object widgetResult(Method m,Object p){return m.getReturnType()==Widget.class?p:zero(m.getReturnType());}
    }
    private static class CoreChatOverlay extends Overlay {
        final String name;
        CoreChatOverlay(String name){this.name=name;setPreferredLocation(new Point(180,320));getBounds().setBounds(180,320,519,165);}
        @Override public String getName(){return name;}
        @Override public Dimension render(Graphics2D g){return null;}
    }
    private static class F {
        final Map<Integer,Widget> widgets=new HashMap<>();
        final int id; int delta=-60, canvasW=1050, canvasH=688; GameState state=GameState.LOGGED_IN;
        final Client client; final CoreChatOverlay overlay; final ResizableModeChat resizer;
        W slot,universe,area;
        F(boolean modern) throws Exception {
            id=modern?InterfaceID.ToplevelPreEoc.CHAT_CONTAINER:InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER;
            overlay=new CoreChatOverlay(modern?"RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT":"RESIZABLE_VIEWPORT_CHATBOX_PARENT");
            client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(p,m,a)->{
                switch(m.getName()){
                    case "getWidget":return widgets.get(a.length==1?(int)a[0]:((int)a[0]<<16)|(int)a[1]);
                    case "getGameState":return state; case "isResized":return true;
                    case "getCanvasWidth":return canvasW; case "getCanvasHeight":return canvasH;
                    default:return zero(m.getReturnType());
                }
            });
            ImprovedChatConfig config=new ImprovedChatConfig(){
                @Override public int heightChange(){return delta;}
                @Override public int widthChange(){return 0;}
                @Override public boolean growInterfaces(){return false;}
            };
            ChatBackgroundGraphic background=new ChatBackgroundGraphic(client,config){
                @Override void resizeTabBar(int dw){} @Override boolean tabBarMatches(int dw){return true;}
                @Override void syncBackground(int w,int h,boolean dialog){} @Override void revertBackground(){}
                @Override void syncBorder(Widget w,boolean reposition,boolean dialog){} @Override void destroyBorder(){}
            };
            ChatDialogBoxes dialogs=new ChatDialogBoxes(client){
                @Override public boolean isDialogOpen(){return false;} @Override void resetDialogPositions(){}
            };
            TopLevelModals modals=new TopLevelModals(client){@Override public boolean isModalOpen(){return false;}};
            Map<Class<?>,Object> dependencies=new HashMap<>();
            dependencies.put(Client.class,client);dependencies.put(ImprovedChatConfig.class,config);
            dependencies.put(SecondarySize.class,new SecondarySize(config));dependencies.put(ChatBackgroundGraphic.class,background);
            dependencies.put(PrivateMessageSplit.class,new PrivateMessageSplit(client,config));dependencies.put(ChatDialogBoxes.class,dialogs);
            dependencies.put(TopLevelModals.class,modals);dependencies.put(RuneLiteHudAnchors.class,new RuneLiteHudAnchors(client,config,null));
            Constructor<?> constructor=ResizableModeChat.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
            Object[] args=new Object[constructor.getParameterCount()];
            for(int i=0;i<args.length;i++){
                Class<?> type=constructor.getParameterTypes()[i];args[i]=dependencies.get(type);
                // Permit the regression to exercise the old dependency before its removal.
                if(args[i]==null){
                    Constructor<?> managerCtor=OverlayManager.class.getDeclaredConstructors()[0];managerCtor.setAccessible(true);
                    OverlayManager manager=(OverlayManager)managerCtor.newInstance(new Object[managerCtor.getParameterCount()]);
                    Field list=OverlayManager.class.getDeclaredField("overlays");list.setAccessible(true);
                    ((java.util.List<Overlay>)list.get(manager)).add(overlay);
                    Constructor<?> legacy=type.getDeclaredConstructor(OverlayManager.class);legacy.setAccessible(true);
                    args[i]=legacy.newInstance(manager);
                }
            }
            resizer=(ResizableModeChat)constructor.newInstance(args); rebuild();
        }
        void rebuild(){
            slot=new W(id,null,519,165);slot.data.put("Forced",new Point(180,320));
            universe=new W(InterfaceID.Chatbox.UNIVERSE,slot.widget,519,165);
            area=new W(InterfaceID.Chatbox.CHATAREA,universe.widget,519,142);
            widgets.put(id,slot.widget);widgets.put(InterfaceID.Chatbox.UNIVERSE,universe.widget);widgets.put(InterfaceID.Chatbox.CHATAREA,area.widget);
        }
        void assertLocation(){assertEquals(new Point(180,320),overlay.getPreferredLocation());assertEquals(new Point(180,320),slot.data.get("Forced"));}
    }
    @Test public void resizeAndRecreatedWidgetsCannotRewriteRuneLitePlacement() throws Exception {
        for(boolean modern:new boolean[]{false,true})for(int height:new int[]{-100,-40,0,28,150}){
            F f=new F(modern);f.delta=height;
            for(int login=0;login<4;login++){
                f.state=GameState.LOGIN_SCREEN;f.widgets.clear();assertNull(f.resizer.apply(false));
                f.rebuild();f.state=GameState.LOGGED_IN;
                f.overlay.setPreferredLocation(new Point(180,320)); // Core reloads its saved position.
                f.resizer.apply(false);f.assertLocation();
                assertEquals(Math.max(0,165+height),f.slot.data.get("Height"));
                f.resizer.apply(false);f.assertLocation();
                f.canvasW=1400;f.canvasH=720;f.resizer.apply(false);f.assertLocation();
            }
        }
    }
    @Test public void unmovedAndResetChatboxStayUnderNativeAnchorOwnership() throws Exception {
        for(boolean modern:new boolean[]{false,true}){
            F f=new F(modern);f.overlay.setPreferredLocation(null);f.slot.data.put("Forced",new Point(-1,-1));
            f.resizer.apply(false);assertNull(f.overlay.getPreferredLocation());
            assertEquals(new Point(-1,-1),f.slot.data.get("Forced"));
            f.resizer.restore();assertNull(f.overlay.getPreferredLocation());
            assertEquals(new Point(-1,-1),f.slot.data.get("Forced"));
        }
    }
    @Test public void collapseSizeChangesAndDisablePreserveSavedAndLivePosition() throws Exception {
        for(boolean modern:new boolean[]{false,true}){
            F f=new F(modern);
            for(int height:new int[]{100,-165,-60,0,28}){f.delta=height;f.resizer.apply(true);f.assertLocation();}
            f.resizer.restore();f.assertLocation();assertEquals(165,f.slot.data.get("Height"));
            assertEquals(new Rectangle(180,320,519,165),f.overlay.getBounds());
        }
    }
}
