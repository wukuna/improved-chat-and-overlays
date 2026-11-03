package com.improvedchat.overlay;

import com.improvedchat.model.FontSize;
import com.improvedchat.model.OverlayMessage;
import com.improvedchat.model.TimestampStyle;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.*;

public class RelativeTimestampTest {
    @Test public void compactAgesFloorAtUnitBoundariesAndClampFutureMessages() {
        long sent=1_700_000_000_000L;
        long[] ages={-1000,0,999,1000,30000,59999,60000,119999,1200000,3599999,3600000,86399999,86400000,172800000,359999999,360000000,360001000};
        String[] expected={"00s","00s","00s","01s","30s","59s","01m","01m","20m","59m","01h","23h","24h","48h","99h",null,null};
        for(int i=0;i<ages.length;i++)assertEquals("Age "+ages[i],expected[i],ChatRenderUtils.formatTimeAgo(sent,sent+ages[i]));
    }

    @Test public void existingAndUnknownStylesKeepClockTimeAndSelectedStyleSurvivesSaving() {
        com.google.gson.Gson gson=new com.google.gson.Gson();
        for(String json:new String[]{"{}","{\"timestampStyle\":null}","{\"timestampStyle\":\"FUTURE_STYLE\"}"})
            assertEquals(TimestampStyle.CLOCK_TIME,gson.fromJson(json,OverlayConfig.class).getTimestampStyle());
        OverlayConfig config=new OverlayConfig();config.setShowTimestamp(true);config.setTimestampStyle(TimestampStyle.TIME_AGO);
        OverlayConfig loaded=gson.fromJson(gson.toJson(config),OverlayConfig.class);
        assertTrue(loaded.isShowTimestamp());assertEquals(TimestampStyle.TIME_AGO,loaded.getTimestampStyle());
    }

    @Test public void retainedMessageAgesOnEachRenderWithoutChangingItsContents() {
        long sent=1_700_000_000_000L;OverlayMessage message=OverlayMessage.gameMessage("Valuable drop: Rune sword",sent,ChatMessageType.GAMEMESSAGE,false);
        OverlayConfig config=new OverlayConfig();config.setShowTimestamp(true);config.setTimestampStyle(TimestampStyle.TIME_AGO);
        assertTrue(text(lines(message,config,sent+1_200_000,"",500)).startsWith("20m "));
        assertTrue(text(lines(message,config,sent+1_260_000,null,500)).startsWith("21m "));
        config.setShowTimestamp(false);assertEquals(message.getMessage(),text(lines(message,config,sent+1_260_000,"[HH:mm]",500)));
        config.setShowTimestamp(true);config.setTimestampStyle(TimestampStyle.CLOCK_TIME);
        assertTrue(text(lines(message,config,sent+1_260_000,"[HH:mm]",500)).startsWith(ChatRenderUtils.formatTimestamp(sent,"[HH:mm]")+" "));
        assertEquals(sent,message.getTimestamp());assertEquals("Valuable drop: Rune sword",message.getMessage());
    }

    @Test public void relativeHeaderWrapsWithSenderAndCanBeRenderedInHistory()throws Exception {
        long sent=1_700_000_000_000L;OverlayConfig config=new OverlayConfig();config.setShowTimestamp(true);config.setTimestampStyle(TimestampStyle.TIME_AGO);
        OverlayMessage message=OverlayMessage.senderMessage("Player","Clan","A longer message that wraps onto more than one line",sent,ChatMessageType.CLAN_CHAT,false);
        List<RenderLine> wrapped=lines(message,config,sent+1_200_000,"[HH:mm]",180);
        assertTrue(wrapped.size()>1);assertTrue(text(wrapped).startsWith("20m "));
        for(RenderLine line:wrapped)assertTrue(line.segments.stream().mapToInt(segment->segment.width).sum()<=180);
        BufferedImage image=new BufferedImage(420,150,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(36,36,36));g.fillRect(0,0,420,150);g.setFont(new Font("SansSerif",Font.PLAIN,16));
        g.setColor(new Color(120,220,190));g.drawString("Time ago",16,24);g.setColor(Color.WHITE);
        int y=53;for(long age:new long[]{30000,1200000,7200000}){g.drawString(ChatRenderUtils.formatTimeAgo(sent,sent+age)+"  Player: Example chat message",16,y);y+=30;}g.dispose();
        javax.imageio.ImageIO.write(image,"png",new java.io.File("build/relative-timestamps.png"));
    }
    private static List<RenderLine> lines(OverlayMessage message,OverlayConfig config,long now,String format,int width) {
        Graphics2D g=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,12));
        try{return ChatRenderUtils.buildMessageLines(message,g.getFontMetrics(),width,now,0,true,Color.WHITE,false,false,FontSize.REGULAR,null,
                config.isShowTimestamp(),format,true,null,Collections.emptyList(),null,config);}finally{g.dispose();}
    }
    private static String text(List<RenderLine> lines){StringBuilder text=new StringBuilder();for(RenderLine line:lines)for(TextSegment segment:line.segments)text.append(segment.text);return text.toString();}
}
