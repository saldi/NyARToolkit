package jp.nyatla.utils.j2se;

import java.awt.image.*;
import jp.nyatla.nyartoolkit.*;
import jp.nyatla.nyartoolkit.core.raster.rgb.*;
import jp.nyatla.nyartoolkit.core.raster.*;
import jp.nyatla.nyartoolkit.core.rasterreader.*;

public class NyARRasterImageIO
{
	/**
	 * i_inの内容を、このイメージにコピーします。
	 * @param i_in
	 * @throws NyARException
	 */
	public static void copy(INyARRgbRaster i_in,BufferedImage o_out) throws NyARException
	{
		assert i_in.getSize().isEqualSize(o_out.getWidth(), o_out.getHeight());
		
		//thisへ転写
		INyARRgbPixelReader reader=i_in.getRgbPixelReader();
		int[] rgb=new int[3];

		for(int y=o_out.getHeight()-1;y>=0;y--){
			for(int x=o_out.getWidth()-1;x>=0;x--){
				reader.getPixel(x,y,rgb);
				o_out.setRGB(x,y,(rgb[0]<<16)|(rgb[1]<<8)|rgb[2]);
			}
		}
		return;
	}
	/**
	 * BIN_8用
	 * @param i_in
	 * @throws NyARException
	 */
	public static void copy(INyARRaster i_in,BufferedImage o_out) throws NyARException
	{
		assert i_in.getSize().isEqualSize(o_out.getWidth(), o_out.getHeight());
		if(i_in.getBufferReader().isEqualBufferType(INyARBufferReader.BUFFERFORMAT_INT1D_BIN_8))
		{
			final int[] buf=(int[])i_in.getBufferReader().getBuffer();
			final int w=o_out.getWidth();
			final int h=o_out.getHeight();
			for(int y=h-1;y>=0;y--){
				for(int x=w-1;x>=0;x--){
					o_out.setRGB(x, y,buf[x+y*w]==0?0:0xffffff);
				}
			}
		}
		return;
	}	
	/**
	 * i_outへこのイメージを出力します。
	 * 
	 * @param i_out
	 * @throws NyARException
	 */
	public static void copy(BufferedImage i_in,INyARRgbRaster o_out) throws NyARException
	{
		assert o_out.getSize().isEqualSize(i_in.getWidth(), i_in.getHeight());
		
		//thisへ転写
		INyARRgbPixelReader reader=o_out.getRgbPixelReader();
		int[] rgb=new int[3];
		for(int y=i_in.getHeight()-1;y>=0;y--){
			for(int x=i_in.getWidth()-1;x>=0;x--){
				int pix=i_in.getRGB(x, y);
				rgb[0]=(pix>>16)&0xff;
				rgb[1]=(pix>>8)&0xff;
				rgb[2]=(pix)&0xff;
				reader.setPixel(x,y,rgb);
			}
		}
		return;
	}
	/**
	 * BIN_8用
	 * @param i_in
	 * @throws NyARException
	 */
	public static void copy(BufferedImage i_in,INyARRaster o_out) throws NyARException
	{
		assert o_out.getSize().isEqualSize(i_in.getWidth(), i_in.getHeight());
		if(o_out.getBufferReader().isEqualBufferType(INyARBufferReader.BUFFERFORMAT_INT1D_BIN_8))
		{
			final int[] buf=(int[])o_out.getBufferReader().getBuffer();
			final int w=i_in.getWidth();
			final int h=i_in.getHeight();
			for(int y=h-1;y>=0;y--){
				for(int x=w-1;x>=0;x--){
					buf[x+y*w]=(i_in.getRGB(x, y)&0xffffff)>0?1:0;
				}
			}
		}
		return;
	}	
}