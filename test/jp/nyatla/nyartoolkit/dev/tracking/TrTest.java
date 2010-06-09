package jp.nyatla.nyartoolkit.dev.tracking;

import java.awt.*;
import java.awt.color.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.media.Buffer;
import jp.nyatla.nyartoolkit.NyARException;
import jp.nyatla.nyartoolkit.core.NyARCode;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.pickup.INyARColorPatt;
import jp.nyatla.nyartoolkit.core.pickup.NyARColorPatt_Perspective_O2;
import jp.nyatla.nyartoolkit.core.raster.NyARBinRaster;
import jp.nyatla.nyartoolkit.core.rasterfilter.rgb2bin.*;
import jp.nyatla.nyartoolkit.core.squaredetect.NyARSquareContourDetector;
import jp.nyatla.nyartoolkit.core.squaredetect.NyARSquareContourDetector_Rle;
import jp.nyatla.nyartoolkit.core.squaredetect.NyARSquareStack;
import jp.nyatla.nyartoolkit.core.transmat.INyARTransMat;
import jp.nyatla.nyartoolkit.core.transmat.NyARRectOffset;
import jp.nyatla.nyartoolkit.core.transmat.NyARTransMat;
import jp.nyatla.nyartoolkit.jmf.utils.*;
import jp.nyatla.nyartoolkit.utils.j2se.NyARRasterImageIO;
import jp.nyatla.nyartoolkit.core.types.*;

import jp.nyatla.nyartoolkit.dev.tracking.detail.NyARDetailTrackItem;
import jp.nyatla.nyartoolkit.dev.tracking.outline.*;




/**
 * @todo
 * 矩形の追跡は動いてるから、位置予測機能と組み合わせて試すこと。
 *
 */

public class TrTest extends Frame implements JmfCaptureListener,MouseMotionListener,INyARMarkerTrackerListener
{


	private final String PARAM_FILE = "../Data/camera_para.dat";

	private final static String CARCODE_FILE = "../Data/patt.hiro";

	private static final long serialVersionUID = -2110888320986446576L;

	private JmfCaptureDevice _capture;

	private JmfNyARRaster_RGB _capraster;

	private int W = 320;

	private int H = 240;

	private NyARMarkerTracker _tr;

	public TrTest() throws NyARException
	{
		setTitle("JmfCaptureTest");
		Insets ins = this.getInsets();
		this.setSize(1024 + ins.left + ins.right, 768 + ins.top + ins.bottom);
		JmfCaptureDeviceList dl = new JmfCaptureDeviceList();
		this._capture = dl.getDevice(0);
		if (!this._capture.setCaptureFormat(JmfCaptureDevice.PIXEL_FORMAT_RGB, W, H, 30.0f)) {
			if (!this._capture.setCaptureFormat(JmfCaptureDevice.PIXEL_FORMAT_YUV, W, H, 30.0f)) {
				throw new NyARException("キャプチャフォーマットが見つかりません。");
			}
		}
		NyARParam ar_param = new NyARParam();
		ar_param.loadARParamFromFile(PARAM_FILE);
		ar_param.changeScreenSize(W, H);

		NyARCode code = new NyARCode(16, 16);
		code.loadARPattFromFile(CARCODE_FILE);
		this._capraster = new JmfNyARRaster_RGB(ar_param, this._capture.getCaptureFormat());
		this._capture.setOnCapture(this);

		addMouseMotionListener(this);
		this._tr=new NyARMarkerTracker(ar_param,this._capraster.getBufferType());

		return;
	}
	int mouse_x;
	int mouse_y;
    public void mouseMoved(MouseEvent A00)
    {
        mouse_x = A00.getX();
        mouse_y = A00.getY();
    }

    public void mouseDragged(MouseEvent A00) {}



	private final String data_file = "../Data/320x240ABGR.raw";
	private void drawPolygon(Graphics g,NyARIntPoint2d[] i_vertex,int i_len)
	{
		int[] x=new int[i_len];
		int[] y=new int[i_len];
		for(int i=0;i<i_len;i++)
		{
			x[i]=i_vertex[i].x;
			y[i]=i_vertex[i].y;
		}
		g.drawPolygon(x,y,i_len);
	}
	private void drawPolygon(Graphics g,NyARDoublePoint2d[] i_vertex,int i_len)
	{
		int[] x=new int[i_len];
		int[] y=new int[i_len];
		for(int i=0;i<i_len;i++)
		{
			x[i]=(int)i_vertex[i].x;
			y[i]=(int)i_vertex[i].y;
		}
		g.drawPolygon(x,y,i_len);
	}	/****************************************/
	class TagValue
	{
		public int counter;
	}
	
	public void OnEnterTracking(NyARMarkerTracker i_sender,NyARTrackItem i_target)
	{
		System.out.println("enter sirial="+i_target.serial);
		i_target.tag=new TagValue();
	}
	public void OnOutlineUpdate(NyARMarkerTracker i_sender,NyAROutlineTrackItem i_target)
	{
		TagValue t=(TagValue)i_target.tag;

		g2.setColor(Color.RED);
		drawPolygon(g2,i_target.vertex,4);
		g2.drawString(Integer.toString(i_target.serial)+":["+t.counter+"%]",i_target.vertex[0].x,i_target.vertex[0].y);
		if(t.counter>30){
			i_target.setUpgradeInfo(80,1);
		}
		t.counter++;
		
	}
	public void OnDetailUpdate(NyARMarkerTracker i_sender,NyARDetailTrackItem i_target)
	{
		g2.setColor(Color.BLUE);
		drawPolygon(g2,i_target.estimate.ideal_vertex,4);
		g2.drawString(Integer.toString(i_target.serial),(int)i_target.estimate.ideal_vertex[0].x,(int)i_target.estimate.ideal_vertex[0].y);
	}
	public void OnLeaveTracking(NyARMarkerTracker i_sender,NyARTrackItem i_target)
	{
		System.out.println("leave sirial="+i_target.serial);
	}
	/****************************************/
	
	
	private Graphics g2;

	public void draw(JmfNyARRaster_RGB i_raster)
	{
		try {
			Insets ins = this.getInsets();
			Graphics g = getGraphics();
			Object[] probe=this._tr._probe();
			NyAROutlineTracker mpt=(NyAROutlineTracker)probe[0];
				
					
			BufferedImage sink = new BufferedImage(i_raster.getWidth(), i_raster.getHeight(), ColorSpace.TYPE_RGB);
			this.g2=sink.getGraphics();
			NyARRasterImageIO.copy(i_raster, sink);
			this._tr.tracking(i_raster,this);
			
			g.drawImage(sink, ins.left, ins.top, this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void onUpdateBuffer(Buffer i_buffer)
	{
		try {

			{// ピックアップ画像の表示
				// 矩形抽出
				synchronized(this._capraster){
					this._capraster.setBuffer(i_buffer);
					draw(this._capraster);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startCapture()
	{
		try {
			this._capture.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startImage()
	{
		try {
			// 試験イメージの読み出し(320x240 BGRAのRAWデータ)
			File f = new File(data_file);
			FileInputStream fs = new FileInputStream(data_file);
			byte[] buf = new byte[(int) f.length() * 4];
			fs.read(buf);
//			INyARRgbRaster ra = NyARRgbRaster_BGRA.wrap(buf, W, H);
//			draw(ra);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args)
	{

		try {
			TrTest mainwin = new TrTest();
			mainwin.setVisible(true);
			mainwin.startCapture();
			// mainwin.startImage();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
