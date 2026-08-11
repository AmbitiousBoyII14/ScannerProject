package myscanne.com;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

public class Prefs {
    private static final String F="ui_prefs";
    private static final String K_T="theme",K_FNT="font",K_D="density",K_M="menu",K_TO="to",K_P="ports",K_MTD="mtd",K_TH="th";

    public static final String[] TN={"Crimson Dark","Ocean Dark","Emerald Dark","Amber Dark","Violet Dark","AMOLED","B&W Light","Purple Light","Daylight"};
    // bg,card,stroke,accent,info,text,muted,isLight
    private static final int[][] TC={
        {0xFF0D0F14,0xFF161A22,0xFF232A36,0xFFFF3B4E,0xFF3D8BFF,0xFFF2F5FA,0xFF8A93A6,0},
        {0xFF0A121F,0xFF12203A,0xFF1E3350,0xFF3D8BFF,0xFF22C55E,0xFFEAF2FF,0xFF7E8DA6,0},
        {0xFF0A1410,0xFF11241C,0xFF1E3A2E,0xFF22C55E,0xFF3D8BFF,0xFFEAFBF1,0xFF7F9C8B,0},
        {0xFF14100A,0xFF241E11,0xFF3A311E,0xFFF5C518,0xFFFF3B4E,0xFFFBF6EA,0xFFA69A7E,0},
        {0xFF0F0B18,0xFF1B142B,0xFF2C2244,0xFF8B5CF6,0xFF22C55E,0xFFF3EEFF,0xFF9488AE,0},
        {0xFF000000,0xFF0A0A0A,0xFF1C1C1C,0xFFFF3B4E,0xFF3D8BFF,0xFFFFFFFF,0xFF8A8A8A,0},
        {0xFFFFFFFF,0xFFF5F5F5,0xFFE0E0E0,0xFF111111,0xFF555555,0xFF111111,0xFF888888,1},
        {0xFFFAFAFF,0xFFFFFFFF,0xFFE0DCF0,0xFF7C3AED,0xFFA78BFA,0xFF1E1B4B,0xFF8B7FAD,1},
        {0xFFF2F5FA,0xFFFFFFFF,0xFFD8DEE9,0xFFE11D48,0xFF2563EB,0xFF0D0F14,0xFF64748B,1},
    };

    private static SharedPreferences sp(Context c){return c.getSharedPreferences(F,Context.MODE_PRIVATE);}
    public static void setTheme(Context c,int i){sp(c).edit().putInt(K_T,i).apply();}
    public static void setFont(Context c,int i){sp(c).edit().putInt(K_FNT,i).apply();}
    public static void setDensity(Context c,int i){sp(c).edit().putInt(K_D,i).apply();}
    public static void setMenu(Context c,int i){sp(c).edit().putInt(K_M,i).apply();}
    public static int theme(Context c){return cI(sp(c).getInt(K_T,0),TN.length);}
    public static int font(Context c){return cI(sp(c).getInt(K_FNT,0),2);}
    public static int density(Context c){return cI(sp(c).getInt(K_D,0),2);}
    public static int menu(Context c){return cI(sp(c).getInt(K_M,0),2);}

    // Scan — default 1000ms, ports 443 only
    public static void setTimeoutMs(Context c,int ms){sp(c).edit().putInt(K_TO,ms).apply();}
    public static int getTimeoutMs(Context c){return sp(c).getInt(K_TO,1000);}
    public static void setPorts(Context c,String p){sp(c).edit().putString(K_P,p).apply();}
    public static String getPorts(Context c){return sp(c).getString(K_P,"443");}
    public static int[] getPortsArray(Context c){
        List<Integer> l=new ArrayList<Integer>();
        for(String p:getPorts(c).split(",")){
            try{int n=Integer.parseInt(p.trim());if(n>0&&n<=65535)l.add(n);}catch(Exception x){}
        }
        if(l.isEmpty())l.add(443);
        int[]a=new int[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);return a;
    }
    public static void setMethod(Context c,int m){sp(c).edit().putInt(K_MTD,m).apply();}
    public static int getMethod(Context c){return cI(sp(c).getInt(K_MTD,0),2);}
    public static String getMethodStr(Context c){return getMethod(c)==1?"HEAD":"GET";}
    public static void setThreads(Context c,int t){sp(c).edit().putInt(K_TH,t).apply();}
    public static int getThreads(Context c){int v=sp(c).getInt(K_TH,50);if(v<1)v=1;if(v>500)v=500;return v;}

    public static int bg(Context c){return TC[theme(c)][0];}
    public static int card(Context c){return TC[theme(c)][1];}
    public static int stroke(Context c){return TC[theme(c)][2];}
    public static int accent(Context c){return TC[theme(c)][3];}
    public static int info(Context c){return TC[theme(c)][4];}
    public static int text(Context c){return TC[theme(c)][5];}
    public static int muted(Context c){return TC[theme(c)][6];}
    public static boolean isLight(Context c){return TC[theme(c)][7]==1;}
    public static int[] palette(int ti){return TC[cI(ti,TC.length)];}
    public static boolean isSans(Context c){return font(c)==1;}
    public static boolean isCompact(Context c){return density(c)==1;}
    public static boolean isGrid(Context c){return menu(c)==1;}
    private static int cI(int v,int max){if(v<0)return 0;if(v>=max)return max-1;return v;}
}
