package fr.inria.hal.annotation;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Achraf
 */

// DUPLICATED !
public class Utilities {    
    
    private static final Logger logger = LoggerFactory.getLogger(Utilities.class);
    
    private static Set<String> dates = new LinkedHashSet<String>();
    
    static {
        Calendar toDay = Calendar.getInstance();
        int todayYear = toDay.get(Calendar.YEAR);
        int todayMonth = toDay.get(Calendar.MONTH) + 1;
        for (int year = todayYear; year >= 1960; year--) {
            int monthYear = (year == todayYear) ? todayMonth : 12;
            for (int month = monthYear; month >= 1; month--) {
                for (int day = daysInMonth(year, month); day >= 1; day--) {
                    StringBuilder date = new StringBuilder();
                    date.append(String.format("%04d", year));
                    date.append("-");
                    date.append(String.format("%02d", month));
                    date.append("-");
                    date.append(String.format("%02d", day));
                    getDates().add(date.toString());
                }
            }
        }
    }
    
    public static void updateDates(String fromDate, String untilDate) {
        boolean isOkDate = true;
        if(untilDate != null)
            isOkDate = false;
        String[] dates1 = new String[dates.size()];
        dates.toArray(dates1);
        for(String date:dates1){            
            if(date.equals(untilDate))
                isOkDate = true;                        
            if(!isOkDate)
                dates.remove(date);
            if(fromDate != null) {
                if(date.equals(fromDate)){
                    isOkDate = false;
                }
            }
        }
    }
        
    private static int daysInMonth(int year, int month) {
        int daysInMonth;
        switch (month) {
            case 1:
            case 3:
            case 5:
            case 7:
            case 8:
            case 10:
            case 12:
                daysInMonth = 31;
                break;
            case 2:
                if (((year % 4 == 0) && (year % 100 != 0)) || (year % 400 == 0)) {
                    daysInMonth = 29;
                } else {
                    daysInMonth = 28;
                }
                break;
            default:
                // returns 30 even for nonexistant months 
                daysInMonth = 30;
        }
        return daysInMonth;
    }
    
    public static boolean isValidDate(String dateString) {
        return dates.contains(dateString);
    }
        
    public static String formatDate(Date date) {
        SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd");
        return dt1.format(date);
    }

    public static Date parseStringDate(String dateString) throws ParseException {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
        Date date = format.parse(dateString);
        return date;
    }

    /**
     * @return the dates
     */
    public static Set<String> getDates() {
        return dates;
    }
}
