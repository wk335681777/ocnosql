package com.ailk.oci.ocnosql.tools;

import java.math.BigInteger;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.hbase.util.Bytes;

public class RegionSplitsUtil {
	final static String MAXMD5 = "FFF";
	final static int rowComparisonLength = MAXMD5.length();
	static byte[] convertToByte(BigInteger bigInteger) {
		String bigIntegerString = bigInteger.toString(16);
		bigIntegerString = StringUtils.leftPad(bigIntegerString,rowComparisonLength, '0');
		return Bytes.toBytes(bigIntegerString);
	}
	public static byte[][] splits(String startKey, String endKey,int numRegions) {
		byte[][] splits = new byte[numRegions][];
		BigInteger lowestKey = new BigInteger(startKey,16);
		BigInteger highestKey = new BigInteger(endKey,16);
		BigInteger range = highestKey.subtract(lowestKey);
		BigInteger regionIncrement = range.divide(BigInteger.valueOf(numRegions));
		lowestKey = lowestKey.add(regionIncrement);
		for (int i = 0; i < numRegions; i++) {
			BigInteger key = lowestKey.add(regionIncrement.multiply(BigInteger.valueOf(i)));
			byte[] b = convertToByte(key);
			splits[i] = b;
		}
		return splits;
	}
	public static byte[][] splits(int numRegions) {
        int total = 0;
        int length = 0;
        if (numRegions < 3) {
            throw new UnsupportedOperationException("the number of ranges must more than 3");
        } 
        else if (numRegions < 16) {
            total = 16 * 16;
            length = 2;
        }
        else if (numRegions < 16 * 16) {
            total = 16 * 16 * 16;
            length = 3;
        } 
        else if (numRegions < 16 * 16 * 16) {
            total = 16 * 16 * 16 * 16;
            length = 4;
        } 
        else {
            throw new UnsupportedOperationException("not support more than 16 * 16 * 16 of ranges");
        }
        byte[][] result = new byte[numRegions][length];
        float div = total / (numRegions-1);
        for (int i = 0; i < numRegions-1; i++) {
            int curr = (int) (i * div);
            byte[] temp = Integer.toHexString(curr).getBytes();
            if (temp.length < length) {
                int j = 0;
                for (; j < length - temp.length; j++) {
                    result[i][j] = '0';
                }
                for (int k = 0; k < temp.length; k++) {
                    result[i][j] = temp[k];
                    j++;
                }
            } else {
                result[i] = temp;
            }
        }
        for(int i = 0;i<length;i++) {
            result[numRegions-1][i] = 'f';
        }
        
        return result;
    }
}
