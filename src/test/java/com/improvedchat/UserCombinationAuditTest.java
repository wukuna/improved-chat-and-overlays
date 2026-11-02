package com.improvedchat;
import com.improvedchat.model.*;
import com.improvedchat.overlay.*;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.client.config.Notification;
import org.junit.Test;
import static org.junit.Assert.*;
public class UserCombinationAuditTest {
 static class P extends ImprovedChatPlugin {final List<String> alerts=new ArrayList<>();@Override public boolean isGameFilterEnabled(){return false;}@Override public boolean isBossKcFilterEnabled(){return false;}@Override protected void sendOverlayNotification(Notification n,String s){alerts.add(s);} }
 static void set(Object o,Class<?> c,String name,Object v)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 static Object def(Class<?> c){if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked") @Test public void filtersAlertsAndHistoryExhaustiveStates()throws Exception{
  P p=new P();boolean[] global={false,false,true,true}; // duplicate collapse, global filter, color rules, player rules
  ImprovedChatConfig c=new ImprovedChatConfig(){@Override public boolean collapseDuplicates(){return global[0];}@Override public boolean useChatFilter(){return global[1];}@Override public boolean enableMessageColorRules(){return global[2];}@Override public boolean includePlayerChatInColorRules(){return global[3];}@Override public String messageColorRules(){return "hello::1::flash";}};
  set(p,ImprovedChatPlugin.class,"config",c);GameState[] state={GameState.LOGGED_IN};
  Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(o,m,a)->m.getName().equals("getGameState")?state[0]:def(m.getReturnType()));set(p,ImprovedChatPlugin.class,"client",client);
  Constructor<?> ctor=net.runelite.client.plugins.PluginManager.class.getDeclaredConstructors()[0];ctor.setAccessible(true);Object[] args=new Object[ctor.getParameterCount()];Class<?>[] types=ctor.getParameterTypes();for(int n=0;n<args.length;n++){if(types[n]==boolean.class)args[n]=false;if(types[n]==net.runelite.client.eventbus.EventBus.class)args[n]=new net.runelite.client.eventbus.EventBus();}set(p,ImprovedChatPlugin.class,"pluginManager",ctor.newInstance(args));
  Field f=ImprovedChatPlugin.class.getDeclaredField("messages");f.setAccessible(true);List<OverlayMessage> history=(List<OverlayMessage>)f.get(p);OverlayConfig overlay=OverlayConfig.preset("Private","private");p.getOverlayConfigs().add(overlay);
  int cases=0;
  for(int flags=0;flags<1024;flags++)for(OverlayFilterMode mode:OverlayFilterMode.values()){
   global[0]=(flags&1)!=0;global[1]=(flags&2)!=0;global[2]=(flags&4)!=0;global[3]=(flags&8)!=0;MessageColorRuleEngine.configure(c);overlay.getTextSettings().setSolidEnabled(global[2]);overlay.getTextSettings().setIncludePlayer(global[3]);overlay.getTextSettings().setSolidRules(c.messageColorRules());
   boolean shown=(flags&16)!=0,enabled=(flags&32)!=0,onlyFlash=(flags&64)!=0,outgoing=(flags&128)!=0,perGlobal=(flags&256)!=0,loggedIn=(flags&512)!=0;
   state[0]=loggedIn?GameState.LOGGED_IN:GameState.LOGIN_SCREEN;overlay.setShow(shown);overlay.setNotification(enabled?Notification.ON:Notification.OFF);overlay.setNotificationTrigger(onlyFlash?AttentionTrigger.FLASH_RULES_ONLY:AttentionTrigger.ALL_NEW_MESSAGES);overlay.setFilterMode(mode);overlay.setFilteredWords("hello");overlay.setUseGlobalChatFilter(perGlobal);
   history.clear();p.alerts.clear();boolean locallyVisible=global[1]||perGlobal||mode!=OverlayFilterMode.HIDE_MATCHES;
   for(int n=0;n<2;n++){OverlayMessage msg=OverlayMessage.senderMessage("Alice",null,"hello",System.currentTimeMillis(),ChatMessageType.PRIVATECHAT,outgoing);history.add(msg);p.notifyOverlays(msg);}
   List<OverlayMessage> selected=p.getMessagesForOverlay(overlay);assertEquals("flags="+flags+" mode="+mode,locallyVisible?(global[0]?1:2):0,selected.size());
   int alerts=shown&&enabled&&loggedIn&&!outgoing&&locallyVisible&&(!onlyFlash||(global[2]&&global[3]))?2:0;assertEquals("flags="+flags+" mode="+mode,alerts,p.alerts.size());
   p.getMessagesForOverlay(overlay);p.getMessagesForOverlay(overlay);assertEquals(alerts,p.alerts.size());cases++;
  }
  MessageColorRuleEngine.configure(null);System.out.println("AUDIT chat filter/history/notification states="+cases);
 }
 @Test public void overlayBooleanPairsSurvivePersistenceAndBounds()throws Exception{
  com.google.gson.Gson gson=new com.google.gson.Gson();List<Method> setters=new ArrayList<>();for(Method m:OverlayConfig.class.getDeclaredMethods())if(m.getName().startsWith("set")&&m.getParameterCount()==1&&m.getParameterTypes()[0]==boolean.class)setters.add(m);setters.sort(Comparator.comparing(Method::getName));
  int cases=0;for(boolean background:new boolean[]{false,true})for(int a=0;a<setters.size();a++)for(int b=a+1;b<setters.size();b++)for(int bits=0;bits<4;bits++){
   OverlayConfig c=OverlayConfig.preset("Saved","all");for(Method m:setters)m.invoke(c,background);setters.get(a).invoke(c,(bits&1)!=0);setters.get(b).invoke(c,(bits&2)!=0);
   c.setWidgetWidth(bits%2==0?150:1024);c.setPaddingHorizontal(100);c.setMaxMessages(bits%2==0?1:100);c.normalize();String encoded=gson.toJson(c);OverlayConfig r=OverlayConfigStore.read(gson,"["+encoded+"]").get(0);assertEquals(encoded,gson.toJson(r));cases++;
  }System.out.println("AUDIT overlay boolean setters="+setters.size()+" persisted pair states="+cases);
 }
 @SuppressWarnings("unchecked") @Test public void characterizeAlertsWhenChatboxConditionHidesOverlay()throws Exception {
  P p=new P();ImprovedChatConfig c=new ImprovedChatConfig(){};set(p,ImprovedChatPlugin.class,"config",c);
  set(p,ImprovedChatPlugin.class,"chatboxVisibility",new com.improvedchat.chatbox.ChatboxVisibility());
  Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(o,m,a)->m.getName().equals("getGameState")?GameState.LOGGED_IN:def(m.getReturnType()));set(p,ImprovedChatPlugin.class,"client",client);
  OverlayConfig overlay=OverlayConfig.preset("Private","private");overlay.setAlwaysVisible(false);overlay.setShow(true);overlay.setNotification(Notification.ON);p.getOverlayConfigs().add(overlay);
  OverlayMessage msg=OverlayMessage.senderMessage("Alice",null,"hello",System.currentTimeMillis(),ChatMessageType.PRIVATECHAT,false);Field f=ImprovedChatPlugin.class.getDeclaredField("messages");f.setAccessible(true);((List<OverlayMessage>)f.get(p)).add(msg);p.notifyOverlays(msg);
  assertNull(new DynamicChatOverlay(p,c,client,null,null,overlay).render(null));assertEquals(1,p.alerts.size());System.out.println("AUDIT observed: only-when-chat-hidden overlay can notify while native chat is open (policy clarification needed)");
 }
}
