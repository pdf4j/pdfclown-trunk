/*
  Copyright 2006-2011 Stefano Chizzolini. http://www.pdfclown.org

  Contributors:
    * Stefano Chizzolini (original code developer, http://www.stefanochizzolini.it)

  This file should be part of the source code distribution of "PDF Clown library"
  (the Program): see the accompanying README files for more info.

  This Program is free software; you can redistribute it and/or modify it under the terms
  of the GNU Lesser General Public License as published by the Free Software Foundation;
  either version 3 of the License, or (at your option) any later version.

  This Program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
  either expressed or implied; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE. See the License for more details.

  You should have received a copy of the GNU Lesser General Public License along with this
  Program (see README files); if not, go to the GNU website (http://www.gnu.org/licenses/).

  Redistribution and use, with or without modification, are permitted provided that such
  redistributions retain the above copyright notice, license and disclaimer, along with
  this list of conditions.
*/

package org.pdfclown.objects;

import java.util.Iterator;

import org.pdfclown.bytes.Buffer;
import org.pdfclown.bytes.IBuffer;
import org.pdfclown.bytes.IOutputStream;
import org.pdfclown.bytes.filters.Filter;
import org.pdfclown.files.File;
import org.pdfclown.tokens.Encoding;
import org.pdfclown.tokens.Keyword;
import org.pdfclown.tokens.Symbol;

/**
  PDF stream object [PDF:1.6:3.2.7].

  @author Stefano Chizzolini (http://www.stefanochizzolini.it)
  @version 0.1.1, 11/01/11
*/
public class PdfStream
  extends PdfDataObject
{
  // <class>
  // <static>
  // <fields>
  private static final byte[] BeginStreamBodyChunk = Encoding.encode(Symbol.LineFeed + Keyword.BeginStream + Symbol.LineFeed);
  private static final byte[] EndStreamBodyChunk = Encoding.encode(Symbol.LineFeed + Keyword.EndStream);
  // </fields>
  // </static>

  // <dynamic>
  // <fields>
  private IBuffer body;
  private PdfDictionary header;

  private PdfObject parent;
  private boolean updated;
  private boolean updateable = true;
  // </fields>

  // <constructors>
  public PdfStream(
    )
  {
    this(
      new PdfDictionary(),
      new Buffer()
      );
  }

  public PdfStream(
    PdfDictionary header
    )
  {
    this(
      header,
      new Buffer()
      );
  }

  public PdfStream(
    IBuffer body
    )
  {
    this(
      new PdfDictionary(),
      body
      );
  }

  public PdfStream(
    PdfDictionary header,
    IBuffer body
    )
  {
    this.header = (PdfDictionary)include(header);

    this.body = body;
    body.clean();
    body.addListener(new IBuffer.IListener()
    {
      @Override
      public void onChange(
        IBuffer buffer
        )
      {update();}
    });
  }
  // </constructors>

  // <interface>
  // <public>
  @Override
  public PdfStream clone(
    File context
    )
  {
    PdfStream clone = (PdfStream)super.clone(context);
    {
      clone.header = header.clone(context);
      clone.body = body.clone();
    }
    return clone;
  }

  /**
    Gets the decoded stream body.
  */
  public IBuffer getBody(
    )
  {
    /*
      NOTE: Encoding filters are removed by default because they belong to a lower layer (token
      layer), so that it's appropriate and consistent to transparently keep the object layer unaware
      of such a facility.
    */
    return getBody(true);
  }

  /**
    Gets the stream body.

    @param decode Defines whether the body has to be decoded.
  */
  public IBuffer getBody(
    boolean decode
    )
  {
    if(decode)
    {
      // Get 'Filter' entry!
      /*
        NOTE: It defines possible encodings applied to the stream.
      */
      PdfDataObject filterObject = header.resolve(PdfName.Filter);
      if(filterObject != null) // Stream encoded.
      {
        header.setUpdateable(false);
        /*
          NOTE: If the stream is encoded, we must decode it before continuing.
        */
        PdfDataObject decodeParms = header.resolve(PdfName.DecodeParms);
        if(filterObject instanceof PdfName) // Single filter.
        {
          PdfDictionary filterDecodeParms = (PdfDictionary)decodeParms;
          body.decode(Filter.get((PdfName)filterObject), filterDecodeParms);
        }
        else // Multiple filters.
        {
          Iterator<PdfDirectObject> filterObjIterator = ((PdfArray)filterObject).iterator();
          Iterator<PdfDirectObject> decodeParmsIterator = (decodeParms != null ? ((PdfArray)decodeParms).iterator() : null);
          while(filterObjIterator.hasNext())
          {
            PdfDictionary filterDecodeParms = (PdfDictionary)(decodeParmsIterator != null ? File.resolve(decodeParmsIterator.next()) : null);
            body.decode(Filter.get((PdfName)File.resolve(filterObjIterator.next())), filterDecodeParms);
          }
        }
        // Update 'Filter' entry!
        header.put(PdfName.Filter,null); // The stream is free from encodings.
        header.setUpdateable(true);
      }
    }
    return body;
  }

  /**
    Gets the stream header.
  */
  public PdfDictionary getHeader(
    )
  {return header;}

  @Override
  public PdfObject getParent(
    )
  {return parent;}

  @Override
  public boolean isUpdateable(
    )
  {return updateable;}

  @Override
  public boolean isUpdated(
    )
  {return updated;}

  @Override
  public void setUpdateable(
    boolean value
    )
  {updateable = value;}

  @Override
  public void writeTo(
    IOutputStream stream,
    File context
    )
  {
    header.setUpdateable(false);

    boolean unencodedBody;
    byte[] bodyData;
    int bodyLength;

    // 1. Header.
    // Encoding.
    PdfDirectObject filterObject = header.get(PdfName.Filter);
    if(filterObject == null) // Unencoded body.
    {
      /*
        NOTE: Header entries related to stream body encoding are temporary, instrumental to the
        current serialization process only.
      */
      unencodedBody = true;

      // Set the filter to apply!
      filterObject = PdfName.FlateDecode; // zlib/deflate filter.
      // Get encoded body data applying the filter to the stream!
      bodyData = body.encode(Filter.get((PdfName)filterObject), null);
      // Set encoded length!
      bodyLength = bodyData.length;
      // Update 'Filter' entry!
      header.put(PdfName.Filter, filterObject);
    }
    else // Encoded body.
    {
      unencodedBody = false;

      // Get encoded body data!
      bodyData = body.toByteArray();
      // Set encoded length!
      bodyLength = (int)body.getLength();
    }
    // Set encoded length!
    header.put(PdfName.Length, new PdfInteger(bodyLength));

    header.writeTo(stream, context);

    // Is the body free from encodings?
    if(unencodedBody)
    {
      // Restore actual header entries!
      header.put(PdfName.Length, new PdfInteger((int)body.getLength()));
      header.put(PdfName.Filter, null);
    }

    // 2. Body.
    stream.write(BeginStreamBodyChunk);
    stream.write(bodyData);
    stream.write(EndStreamBodyChunk);

    header.setUpdateable(true);
  }
  // </public>

  // <protected>
  @Override
  protected boolean isVirtual(
    )
  {return false;}

  @Override
  protected void setUpdated(
    boolean value
    )
  {updated = value;}

  @Override
  protected void setVirtual(
    boolean value
    )
  {/* NOOP */}
  // </protected>

  // <internal>
  @Override
  void setParent(
    PdfObject value
    )
  {parent = value;}
  // </internal>
  // </interface>
  // </dynamic>
  // </class>
}