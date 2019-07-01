package org.hisp.dhis.smscompression.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hisp.dhis.smscompression.SMSCompressionException;
import org.hisp.dhis.smscompression.SMSConsts;
import org.hisp.dhis.smscompression.SMSConsts.MetadataType;
import org.hisp.dhis.smscompression.models.SMSAttributeValue;
import org.hisp.dhis.smscompression.models.SMSDataValue;
import org.hisp.dhis.smscompression.models.SMSMetadata;

public class ValueUtil
{

    public static void writeAttributeValues( List<SMSAttributeValue> values, SMSMetadata meta,
        BitOutputStream outStream )
        throws SMSCompressionException
    {
        int attributeBitLen = IDUtil.getBitLengthForList( meta.getType( MetadataType.TRACKED_ENTITY_ATTRIBUTE ) );
        outStream.write( attributeBitLen, SMSConsts.VARLEN_BITLEN );
        for ( Iterator<SMSAttributeValue> valIter = values.iterator(); valIter.hasNext(); )
        {
            SMSAttributeValue val = valIter.next();
            int idHash = BinaryUtils.hash( val.getAttribute(), attributeBitLen );
            outStream.write( idHash, attributeBitLen );
            writeString( val.getValue(), outStream );

            int separator = valIter.hasNext() ? 1 : 0;
            outStream.write( separator, 1 );
        }
    }

    public static List<SMSAttributeValue> readAttributeValues( SMSMetadata meta, BitInputStream inStream )
        throws SMSCompressionException
    {
        ArrayList<SMSAttributeValue> values = new ArrayList<>();
        int attributeBitLen = inStream.read( SMSConsts.VARLEN_BITLEN );
        Map<Integer, String> idLookup = IDUtil.getIDLookup( meta.getType( MetadataType.TRACKED_ENTITY_ATTRIBUTE ),
            attributeBitLen );

        for ( int valSep = 1; valSep == 1; valSep = inStream.read( 1 ) )
        {
            int idHash = inStream.read( attributeBitLen );
            String id = idLookup.get( idHash );
            String val = readString( inStream );
            values.add( new SMSAttributeValue( id, val ) );
        }

        return values;
    }

    public static void writeString( String s, BitOutputStream outStream )
        throws SMSCompressionException
    {
        for ( char c : s.toCharArray() )
        {
            outStream.write( c, SMSConsts.CHAR_BITLEN );
        }
        // Null terminator
        outStream.write( 0, SMSConsts.CHAR_BITLEN );
    }

    public static String readString( BitInputStream inStream )
        throws SMSCompressionException
    {
        String s = "";
        do
        {
            int i = inStream.read( SMSConsts.CHAR_BITLEN );
            if ( i == 0 )
                break;
            s += (char) i;
        }
        while ( true );
        return s;
    }

    public static void writeDate( Date d, BitOutputStream outStream )
        throws SMSCompressionException
    {
        long epochSecs = d.getTime() / 1000;
        outStream.write( (int) epochSecs, SMSConsts.EPOCH_DATE_BITLEN );
    }

    public static Date readDate( BitInputStream inStream )
        throws SMSCompressionException
    {
        long epochSecs = inStream.read( SMSConsts.EPOCH_DATE_BITLEN );
        Date dateVal = new Date( epochSecs * 1000 );
        return dateVal;
    }

    public static Map<String, List<SMSDataValue>> groupDataValues( List<SMSDataValue> values )
    {
        HashMap<String, List<SMSDataValue>> map = new HashMap<>();
        for ( SMSDataValue val : values )
        {
            String catOptionCombo = val.getCategoryOptionCombo();
            if ( !map.containsKey( catOptionCombo ) )
            {
                ArrayList<SMSDataValue> list = new ArrayList<>();
                map.put( catOptionCombo, list );
            }
            List<SMSDataValue> list = map.get( catOptionCombo );
            list.add( val );
        }
        return map;
    }

    // TODO: No error handling for missing IDs at the moment
    public static void writeDataValues( List<SMSDataValue> values, SMSMetadata meta, BitOutputStream outStream )
        throws SMSCompressionException
    {
        int catOptionComboBitLen = IDUtil.getBitLengthForList( meta.getType( MetadataType.CATEGORY_OPTION_COMBO ) );
        outStream.write( catOptionComboBitLen, SMSConsts.VARLEN_BITLEN );
        int dataElementBitLen = IDUtil.getBitLengthForList( meta.getType( MetadataType.DATA_ELEMENT ) );
        outStream.write( dataElementBitLen, SMSConsts.VARLEN_BITLEN );

        Map<String, List<SMSDataValue>> valMap = groupDataValues( values );

        for ( Iterator<String> keyIter = valMap.keySet().iterator(); keyIter.hasNext(); )
        {
            String catOptionCombo = keyIter.next();
            int catOptionComboHash = BinaryUtils.hash( catOptionCombo, catOptionComboBitLen );
            outStream.write( catOptionComboHash, catOptionComboBitLen );
            List<SMSDataValue> vals = valMap.get( catOptionCombo );

            for ( Iterator<SMSDataValue> valIter = vals.iterator(); valIter.hasNext(); )
            {
                SMSDataValue val = valIter.next();
                int deHash = BinaryUtils.hash( val.getDataElement(), dataElementBitLen );
                outStream.write( deHash, dataElementBitLen );
                writeString( val.getValue(), outStream );

                int separator = valIter.hasNext() ? 1 : 0;
                outStream.write( separator, 1 );
            }

            int separator = keyIter.hasNext() ? 1 : 0;
            outStream.write( separator, 1 );
        }
    }

    public static List<SMSDataValue> readDataValues( SMSMetadata meta, BitInputStream inStream )
        throws SMSCompressionException
    {
        int catOptionComboBitLen = inStream.read( SMSConsts.VARLEN_BITLEN );
        int dataElementBitLen = inStream.read( SMSConsts.VARLEN_BITLEN );
        Map<Integer, String> cocIDLookup = IDUtil.getIDLookup( meta.getType( MetadataType.CATEGORY_OPTION_COMBO ),
            catOptionComboBitLen );
        Map<Integer, String> deIDLookup = IDUtil.getIDLookup( meta.getType( MetadataType.DATA_ELEMENT ),
            dataElementBitLen );
        ArrayList<SMSDataValue> values = new ArrayList<>();

        for ( int cocSep = 1; cocSep == 1; cocSep = inStream.read( 1 ) )
        {
            int catOptionComboHash = inStream.read( catOptionComboBitLen );
            String catOptionCombo = cocIDLookup.get( catOptionComboHash );
            for ( int valSep = 1; valSep == 1; valSep = inStream.read( 1 ) )
            {
                int dataElementHash = inStream.read( dataElementBitLen );
                String dataElement = deIDLookup.get( dataElementHash );
                String value = readString( inStream );
                values.add( new SMSDataValue( catOptionCombo, dataElement, value ) );
            }
        }

        return values;
    }
}
