package com.flightcomputer.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Client-side Xaero waypoint file adapter. */
public final class WaypointMapProvider {
    private static final long RESCAN_TICKS = 10L;
    private final List<FlightMapMarker> markers = new ArrayList<>();
    private long nextRefreshTick;
    private Path lastFile;
    private long lastModified = Long.MIN_VALUE;

    public void tick(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || level == null || minecraft.player == null || minecraft.gameDirectory == null) return;
        if (minecraft.level != level) return;
        long now = level.getGameTime();
        if (now < nextRefreshTick) return;
        nextRefreshTick = now + RESCAN_TICKS;

        Path file = locateWaypointFile(minecraft, level);
        if (file == null) { markers.clear(); lastFile = null; lastModified = Long.MIN_VALUE; return; }
        long modified = lastModified(file);
        if (file.equals(lastFile) && modified == lastModified) return;
        lastFile = file; lastModified = modified; load(file);
    }

    public List<FlightMapMarker> markers() { return List.copyOf(markers); }
    public boolean isAvailable() { return lastFile != null; }
    public void clear() { markers.clear(); nextRefreshTick=0L; lastFile=null; lastModified=Long.MIN_VALUE; }

    private void load(Path file) {
        List<FlightMapMarker> next = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || !line.regionMatches(true,0,"waypoint:",0,9)) continue;
                String[] fields=line.split(":",-1); if(fields.length<6)continue;
                Integer x=null,y=null,z=null;
                // Xaero format: waypoint:name:initials:x:y:z:color:disabled:type:set:...
                // Find the first numeric X/Y/Z tuple so extra fields remain harmless.
                for(int i=2;i+2<fields.length;i++){
                    Integer px=parseInt(fields[i]), py=parseInt(fields[i+1]), pz=parseInt(fields[i+2]);
                    if(px!=null&&py!=null&&pz!=null){x=px;y=py;z=pz;break;}
                }
                if(x==null||y==null||z==null)continue;
                String name=fields[1].replace("\\:",":").replace("\\\\","\\");
                next.add(new FlightMapMarker(FlightMapMarker.Type.WAYPOINT,name.isBlank()?"Waypoint":name,x+.5D,y+.5D,z+.5D));
            }
        }catch(IOException ignored){return;}
        markers.clear();markers.addAll(next);
    }

    private Path locateWaypointFile(Minecraft minecraft, ClientLevel level) {
        String world=minecraft.getCurrentServer()!=null?"Multiplayer_"+sanitizeServerAddress(minecraft.getCurrentServer().ip):singleplayerWorldName(minecraft);
        String dimension=dimensionFolder(level); if(dimension==null)return null;
        // Xaero's current persistent waypoint store is XaeroWaypoints/<world>/<dimension>/waypoints.txt.
        // Keep the older minimap location as a compatibility fallback.
        List<Path> roots=List.of(minecraft.gameDirectory.toPath().resolve("XaeroWaypoints"),minecraft.gameDirectory.toPath().resolve("xaero").resolve("minimap"));
        for(Path root:roots){
            if(!Files.isDirectory(root))continue;
            Path direct=root.resolve(world).resolve(dimension).resolve("waypoints.txt");
            if(Files.isRegularFile(direct))return direct;
            String normalized=normalize(world), addressToken=normalize(sanitizeServerAddress(minecraft.getCurrentServer()==null?world:minecraft.getCurrentServer().ip));
            try(var children=Files.list(root)){
                Path match=children.filter(Files::isDirectory)
                        .filter(path->{String n=normalize(path.getFileName().toString());return n.equals(normalized)||(!addressToken.isBlank()&&n.contains(addressToken));})
                        .map(path->path.resolve(dimension).resolve("waypoints.txt"))
                        .filter(Files::isRegularFile).findFirst().orElse(null);
                if(match!=null)return match;
            }catch(IOException ignored){}
        }
        return null;
    }

    private String dimensionFolder(ClientLevel level) {
        return switch(level.dimension().location().toString()){
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "the_nether";
            case "minecraft:the_end" -> "the_end";
            default -> level.dimension().location().toString().replace(':','_').replace('/','_');
        };
    }
    private String singleplayerWorldName(Minecraft minecraft){if(minecraft.getSingleplayerServer()==null)return"unknown";String name=minecraft.getSingleplayerServer().getWorldData().getLevelName();return name==null||name.isBlank()?"unknown":name;}
    private long lastModified(Path path){try{return Files.getLastModifiedTime(path).toMillis();}catch(IOException ignored){return Long.MIN_VALUE;}}
    private Integer parseInt(String value){try{return Integer.valueOf(value.trim());}catch(NumberFormatException ignored){return null;}}
    private String normalize(String value){return value.toLowerCase(Locale.ROOT).replace(' ','_').replace('-','_');}
    private String sanitizeServerAddress(String address){return address.replace(':','_').replace('/','_').replace('\\','_');}
}
