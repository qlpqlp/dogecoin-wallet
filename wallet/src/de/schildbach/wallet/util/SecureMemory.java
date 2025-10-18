/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.util;

import java.util.Arrays;

/**
 * Utility class for secure memory clearing operations.
 * 
 * This class provides methods to securely clear sensitive data from memory
 * to prevent it from being accessible through memory dumps or other
 * forensic analysis techniques.
 * 
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public final class SecureMemory {
    
    private SecureMemory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Securely clear a char array by overwriting it with zeros.
     * This is particularly important for passwords and other sensitive text data.
     * 
     * @param array The char array to clear
     */
    public static void clear(char[] array) {
        if (array != null) {
            Arrays.fill(array, '\0');
        }
    }
    
    /**
     * Securely clear a byte array by overwriting it with zeros.
     * This is important for private keys, seeds, and other binary data.
     * 
     * @param array The byte array to clear
     */
    public static void clear(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }
    
    /**
     * Securely clear a String by overwriting its internal char array.
     * Note: This only works if the String hasn't been interned.
     * 
     * @param str The String to clear
     */
    public static void clear(String str) {
        if (str != null) {
            // Use reflection to access the internal char array
            try {
                java.lang.reflect.Field valueField = String.class.getDeclaredField("value");
                valueField.setAccessible(true);
                char[] value = (char[]) valueField.get(str);
                if (value != null) {
                    Arrays.fill(value, '\0');
                }
            } catch (Exception e) {
                // If reflection fails, we can't clear the String
                // This is a limitation of Java's String immutability
            }
        }
    }
    
    /**
     * Securely clear a StringBuilder by overwriting its internal buffer.
     * 
     * @param sb The StringBuilder to clear
     */
    public static void clear(StringBuilder sb) {
        if (sb != null) {
            // Clear the StringBuilder's internal buffer
            try {
                java.lang.reflect.Field valueField = StringBuilder.class.getSuperclass().getDeclaredField("value");
                valueField.setAccessible(true);
                char[] value = (char[]) valueField.get(sb);
                if (value != null) {
                    Arrays.fill(value, '\0');
                }
                sb.setLength(0);
            } catch (Exception e) {
                // If reflection fails, just clear the length
                sb.setLength(0);
            }
        }
    }
    
    /**
     * Securely clear a StringBuffer by overwriting its internal buffer.
     * 
     * @param sb The StringBuffer to clear
     */
    public static void clear(StringBuffer sb) {
        if (sb != null) {
            // Clear the StringBuffer's internal buffer
            try {
                java.lang.reflect.Field valueField = StringBuffer.class.getSuperclass().getDeclaredField("value");
                valueField.setAccessible(true);
                char[] value = (char[]) valueField.get(sb);
                if (value != null) {
                    Arrays.fill(value, '\0');
                }
                sb.setLength(0);
            } catch (Exception e) {
                // If reflection fails, just clear the length
                sb.setLength(0);
            }
        }
    }
    
    /**
     * Securely clear multiple char arrays at once.
     * 
     * @param arrays Variable number of char arrays to clear
     */
    public static void clearAll(char[]... arrays) {
        for (char[] array : arrays) {
            clear(array);
        }
    }
    
    /**
     * Securely clear multiple byte arrays at once.
     * 
     * @param arrays Variable number of byte arrays to clear
     */
    public static void clearAll(byte[]... arrays) {
        for (byte[] array : arrays) {
            clear(array);
        }
    }
    
    /**
     * Securely clear multiple Strings at once.
     * 
     * @param strings Variable number of Strings to clear
     */
    public static void clearAll(String... strings) {
        for (String str : strings) {
            clear(str);
        }
    }
    
    /**
     * Securely clear a 2D byte array (useful for clearing matrices or grids).
     * 
     * @param array The 2D byte array to clear
     */
    public static void clear(byte[][] array) {
        if (array != null) {
            for (byte[] subArray : array) {
                clear(subArray);
            }
        }
    }
    
    /**
     * Securely clear a 2D char array (useful for clearing matrices or grids).
     * 
     * @param array The 2D char array to clear
     */
    public static void clear(char[][] array) {
        if (array != null) {
            for (char[] subArray : array) {
                clear(subArray);
            }
        }
    }
    
    /**
     * Create a secure wrapper for sensitive data that automatically clears
     * the data when the wrapper is garbage collected or explicitly cleared.
     * 
     * @param <T> The type of sensitive data
     */
    public static class SecureWrapper<T> {
        private T data;
        private boolean cleared = false;
        
        public SecureWrapper(T data) {
            this.data = data;
        }
        
        public T get() {
            if (cleared) {
                throw new IllegalStateException("Data has been securely cleared");
            }
            return data;
        }
        
        public void clear() {
            if (!cleared && data != null) {
                if (data instanceof char[]) {
                    SecureMemory.clear((char[]) data);
                } else if (data instanceof byte[]) {
                    SecureMemory.clear((byte[]) data);
                } else if (data instanceof String) {
                    SecureMemory.clear((String) data);
                }
                data = null;
                cleared = true;
            }
        }
        
        @Override
        @SuppressWarnings("deprecation")
        protected void finalize() throws Throwable {
            try {
                clear();
            } finally {
                super.finalize();
            }
        }
    }
}
