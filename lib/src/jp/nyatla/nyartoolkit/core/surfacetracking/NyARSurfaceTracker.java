package jp.nyatla.nyartoolkit.core.surfacetracking;

import java.io.FileInputStream;

import jp.nyatla.nyartoolkit.core.ar2.base.AR2Handle;
import jp.nyatla.nyartoolkit.core.marker.nft.NyARNftFsetFile;
import jp.nyatla.nyartoolkit.core.marker.nft.NyARNftIsetFile;
import jp.nyatla.nyartoolkit.core.marker.nft.NyARSurfaceFeatures;
import jp.nyatla.nyartoolkit.core.param.NyARParam;
import jp.nyatla.nyartoolkit.core.param.distfactor.INyARCameraDistortionFactor;
import jp.nyatla.nyartoolkit.core.raster.gs.INyARGrayscaleRaster;
import jp.nyatla.nyartoolkit.core.raster.gs.NyARGrayscaleRaster;
import jp.nyatla.nyartoolkit.core.raster.rgb.INyARRgbRaster;
import jp.nyatla.nyartoolkit.core.raster.rgb.NyARRgbRaster;
import jp.nyatla.nyartoolkit.core.rasterdriver.rgb2gs.INyARRgb2GsFilterRgbAve;
import jp.nyatla.nyartoolkit.core.surfacetracking.feature.INyARVisibleFeatureExtractor;
import jp.nyatla.nyartoolkit.core.surfacetracking.feature.NyARSurfaceFeatureIndexSelector;
import jp.nyatla.nyartoolkit.core.surfacetracking.feature.NyARSurfaceFeatureItem;
import jp.nyatla.nyartoolkit.core.surfacetracking.feature.NyARSurfaceFeaturesPtr;
import jp.nyatla.nyartoolkit.core.surfacetracking.feature.NyARVisibleFeatureExtractor;
import jp.nyatla.nyartoolkit.core.surfacetracking.rasterdriver.INyARTemplateMatchingDriver;
import jp.nyatla.nyartoolkit.core.surfacetracking.rasterdriver.NyARTemplateMatchingDriver_INT1D;
import jp.nyatla.nyartoolkit.core.types.NyARBufferType;
import jp.nyatla.nyartoolkit.core.types.NyARDoublePoint2d;
import jp.nyatla.nyartoolkit.core.types.NyARDoublePoint3d;
import jp.nyatla.nyartoolkit.core.types.NyARIntPoint2d;
import jp.nyatla.nyartoolkit.core.types.NyARIntSize;
import jp.nyatla.nyartoolkit.core.types.matrix.NyARDoubleMatrix44;


/**
 * 過去に入力された最大3個の姿勢行列を元に、サーフェイス特徴を追跡し、三次元座標とのペアを生成する。
 *
 */
public class NyARSurfaceTracker
{
	/**
	 * パッチの候補点の保持クラス。
	 */
	private class PatchImagePositions
	{
		/**
		 * パッチの候補点。観察座標。
		 */
		public NyARIntPoint2d[] pos=NyARIntPoint2d.createArray(3);
		private NyARDoublePoint2d _tmp=new NyARDoublePoint2d();
		private INyARCameraDistortionFactor _ref_df;
		public PatchImagePositions(INyARCameraDistortionFactor i_ref_distortionFactor)
		{
			this._ref_df=i_ref_distortionFactor;
		}
		/**
		 * 射影変換行列ログから、候補点を計算して、メンバ変数posへ格納する。
		 * @param i_log
		 * @return
		 * 候補点の数
		 */
		public int makeCandidatePos(NyARSurfaceFeatureItem i_cai,NyARSurfaceTransMatrixSetFifo i_log)
		{
			NyARDoublePoint2d tmp=this._tmp;
			NyARIntPoint2d[] p=this.pos;
			double x=i_cai.x;
			double y=i_cai.y;
			p[0].x=(int)x;
			p[0].y=(int)y;
			int num_of_log=i_log.num_of_item;
			if(num_of_log>=3){
				i_log.items[1].calculate2dPos(i_cai.ref_feature.mx,i_cai.ref_feature.my, tmp);
				this._ref_df.ideal2Observ(tmp, tmp);
				p[1].x = (int) (2 * x - tmp.x);
				p[1].y = (int) (2 * y - tmp.y);
				double x1=tmp.x;
				double y1=tmp.y;
				i_log.items[2].calculate2dPos(i_cai.ref_feature.mx,i_cai.ref_feature.my, tmp);
				this._ref_df.ideal2Observ(tmp, tmp);
				p[2].x = (int) (3 * x - 3 * x1 + tmp.x);
				p[2].y = (int) (3 * y - 3 * y1 + tmp.y);
				
				return 3;
			}else if(num_of_log>=2){
				i_log.items[1].calculate2dPos(i_cai.ref_feature.mx,i_cai.ref_feature.my, tmp);
				this._ref_df.ideal2Observ(tmp, tmp);
				p[1].x = (int) (2 * x - tmp.x);
				p[1].y = (int) (2 * y - tmp.y);
				return 2;
			}
			return 1;
		}
	}
	
	public final static int AR2_DEFAULT_SEARCH_FEATURE_NUM = 10;
	private final static int AR2_DEFAULT_TS1 = 6;
	private final static int AR2_DEFAULT_TS2 = 6;
	private final static double AR2_DEFAULT_SIM_THRESH = 0.5;

	private NyARParam _ref_cparam;
	private double simThresh;
	private NyARSurfaceFeatures _candidate;
	private NyARSurfaceFeatures _candidate2;
	private INyARVisibleFeatureExtractor _feature_selector;
	private int searchFeatureNum;
	public NyARSurfaceTracker(NyARParam i_param_ref,int i_max_search_feature_num)
	{
		this._candidate = new NyARSurfaceFeatures(NyARSurfaceFeatures.AR2_TRACKING_CANDIDATE_MAX + 1);
		this._candidate2 = new NyARSurfaceFeatures(NyARSurfaceFeatures.AR2_TRACKING_CANDIDATE_MAX + 1);
		this._feature_selector = new NyARVisibleFeatureExtractor(i_param_ref.getScreenSize(),i_param_ref.getDistortionFactor());
		this.__pcpoints=new PatchImagePositions(i_param_ref.getDistortionFactor());

		this._ref_cparam = i_param_ref;
		this.searchFeatureNum = i_max_search_feature_num;
		this.simThresh = AR2_DEFAULT_SIM_THRESH;

		this._ctrans_log =new NyARSurfaceTransMatrixSetFifo(3);
		this._prev_selected_features=new NyARFeatureCoordPtrList(this.searchFeatureNum);
		this.__selected_features=new NyARSurfaceFeaturesPtr(this.searchFeatureNum);
		return;
	}



	/**
	 * トラッキング状態をリセットする。
	 */
	public void resetTrackingLog()
	{
		this._ctrans_log.init();
		this._prev_selected_features.clear();
		return;
	}

	private final static double AR2_DEFALUT_TRACKING_SD_THRESH = 5.0;
	private NyARTemplatePatchImage __template_patch=new NyARTemplatePatchImage(AR2_DEFAULT_TS1,AR2_DEFAULT_TS2); 
	private NyARSurfaceFeatureIndexSelector __index_selecter=new NyARSurfaceFeatureIndexSelector();
	private NyARSurfaceFeaturesPtr __selected_features;
	private NyARSurfaceTransMatrixSetFifo _ctrans_log;
	private NyARFeatureCoordPtrList _prev_selected_features;
	private PatchImagePositions __pcpoints;	
	private INyARTemplateMatchingDriver _last_driver;
	private INyARGrayscaleRaster _last_raster=null;
	/**
	 * i_rasiterの画像から、i_surfaceにマッチするパターンを検出して、その理想座標と３次元座標セットを返す。
	 * 検出した頂点セットは、o_pos2dとo_pos3dへ最大i_num個出力する。
	 * @param i_raster
	 * 現在の画像
	 * @param i_surface
	 * 検出すべきサーフェイスセット
	 * @param i_trans
	 * 現在の姿勢変換行列
	 * @param o_pos2d
	 * 出力パラメータ。画面上の理想点。
	 * オブジェクトの配列を指定すること。
	 * @param o_pos3d
	 * 出力パラメータ。三次元サーフェイス座標。
	 * オブジェクトの配列を指定すること。
	 * @param i_num
	 * 返却数。この数値は、コンストラクタに与えた最大数以下である必要がある。o_pos2dとo_pos3dは、この数値より大きい配列でなければならない。
	 * @return
	 * 検出した頂点セットの数。
	 * @throws NyARException
	 */
	public int tracking(INyARGrayscaleRaster i_raster,NyARSurfaceDataSet i_surface,NyARDoubleMatrix44 i_trans,NyARDoublePoint2d[] o_pos2d,NyARDoublePoint3d[] o_pos3d,int i_num)
	{
		//テンプレートドライバの更新
		INyARTemplateMatchingDriver tmd;
		if(this._last_raster!=i_raster){
			tmd=this._last_driver=new NyARTemplateMatchingDriver_INT1D(i_raster);
		}else{
			tmd=this._last_driver;
		}
		//射影変換行列の計算とログへの追加
		NyARSurfaceTransMatrixSet tlog=this._ctrans_log.preAdd();
		tlog.setValue(this._ref_cparam.getPerspectiveProjectionMatrix(),i_trans);

		
		//可視な候補を選択する。(一時リスト)
		this._feature_selector.extractVisibleFeatures(i_surface.fset,tlog, this._candidate, this._candidate2);
		PatchImagePositions pcpoints=this.__pcpoints;
		
		//load screen size.
		NyARIntSize s = this._ref_cparam.getScreenSize();
		
		//頂点選択クラス類の初期化
		NyARSurfaceFeatureIndexSelector index_selecter=this.__index_selecter;
		NyARSurfaceFeaturesPtr selected_features=this.__selected_features;
		selected_features.clear();
		//最大返却数の決定
		int max_feature=i_num>this.__selected_features.getArraySize()?this.__selected_features.getArraySize():i_num;

		int num = 0;
		NyARSurfaceFeatures current_candidate = this._candidate;
		for (int i = max_feature-1; i>=0 ;i--){
			//高精度を優先して探索。なければ低精度に切り替える。切替は1度だけ。出力は座標集合。
			int k = index_selecter.ar2SelectTemplate(current_candidate, this._prev_selected_features, selected_features, s);
			if (k<0) {
				if (current_candidate == this._candidate2) {
					break;
				}
				current_candidate = this._candidate2;
				//未選択なら終了
				k = index_selecter.ar2SelectTemplate(current_candidate, this._prev_selected_features, selected_features, s);
				if (k < 0){
					break;
				}
			}
			//候補kを確保
			NyARSurfaceFeatureItem cai = current_candidate.getItem(k);

			
			//可視な点について、トラッキングするためのパッチ画像を生成
			NyARTemplatePatchImage template_ = this.__template_patch;			
			template_.makeFromReferenceImage((int) (cai.x + 0.5), (int) (cai.y + 0.5),tlog.ctrans,this._ref_cparam.getDistortionFactor(),i_surface.iset.items[cai.scale]);

			//パッチ画像の内容をチェック？
			if (template_.vlen * template_.vlen >= (template_.xsize) * (template_.ysize) * AR2_DEFALUT_TRACKING_SD_THRESH * AR2_DEFALUT_TRACKING_SD_THRESH)
			{
				//射影変換行列ログから候補点を作る。
				int number_of_point=pcpoints.makeCandidatePos(cai, this._ctrans_log);			
	
				//画像からテンプレートを検索
				double sim=tmd.ar2GetBestMatching(template_,pcpoints.pos,number_of_point,o_pos2d[num]);
				//類似値が一定以上なら、保存
				if (sim > this.simThresh) {
					if(selected_features.push(cai)==null){
						break;//最大値に達したら終わり
					}
					this._ref_cparam.getDistortionFactor().observ2Ideal(o_pos2d[num],o_pos2d[num]);
					o_pos3d[num].x= cai.ref_feature.mx;
					o_pos3d[num].y = cai.ref_feature.my;
					o_pos3d[num].z = 0;
					//選択した得量を記録
					num++;
				}
			}
			//選択された候補を取り外す。
			current_candidate.remove(k);
			
		}
		// 過去ログへ記録
		this._prev_selected_features.clear();
		for (int i = 0; i < selected_features.getLength(); i++) {
			this._prev_selected_features.push(selected_features.getItem(i).ref_feature);
		}
		return num;
	}
	public static void main(String[] args)
	{
		NyARDoubleMatrix44 DEST_MAT=new NyARDoubleMatrix44(
				new double[]{
				0.983216579802738	,0.004789670338920735	,-0.182379395452632	,-190.59060778155634,
				0.012860128650301084,-0.9989882776886819	,0.04309405286235391,64.04490608650205,
				-0.18198846949444153,-0.04471620834836307	,-0.9822833723761636,616.6427501051592,
				0,0,0,1});
		NyARDoubleMatrix44 SRC_MAT=new NyARDoubleMatrix44(new double[]{
			0.984363556,	0.00667689135,	-0.176022261,	-191.179672,
			0.0115975942,	-0.999569774,	0.0269410834,	63.0028076,
			-0.175766647,	-0.0285612550,	-0.984017432,	611.758728,
			0,0,0,1});

		try {
			String img_file="../Data/testcase/test.raw";
			String cparam=	"../Data/testcase/camera_para5.dat";
			String fsetfile="../Data/testcase/pinball.fset";
			String isetfile="../Data/testcase/pinball.iset5";
			//カメラパラメータ
			NyARParam param=NyARParam.loadFromARParamFile(new FileInputStream(cparam));
			param.changeScreenSize(640,480);

			
			NyARGrayscaleRaster gs=NyARGrayscaleRaster.createInstance(640,480);
			//試験画像の準備
			{
				INyARRgbRaster rgb=NyARRgbRaster.createInstance(640,480,NyARBufferType.BYTE1D_B8G8R8X8_32);
				FileInputStream fs = new FileInputStream(img_file);
				fs.read((byte[])rgb.getBuffer());
				INyARRgb2GsFilterRgbAve filter=(INyARRgb2GsFilterRgbAve) rgb.createInterface(INyARRgb2GsFilterRgbAve.class);
				filter.convert(gs);			
			}
			NyARNftFsetFile fset=NyARNftFsetFile.loadFromFsetFile(new FileInputStream(fsetfile));
			NyARNftIsetFile iset=NyARNftIsetFile.loadFromIsetFile(new FileInputStream(isetfile));
			NyARSurfaceTracker st=new NyARSurfaceTracker(param,16);
			NyARSurfaceDataSet sd=new NyARSurfaceDataSet(iset,fset);
			NyARDoubleMatrix44 ret=new NyARDoubleMatrix44();
			ret.setValue(SRC_MAT);
			NyARDoublePoint2d[] o_pos2d=NyARDoublePoint2d.createArray(16);
			NyARDoublePoint3d[] o_pos3d=NyARDoublePoint3d.createArray(16);
			st.tracking(gs, sd,ret, o_pos2d, o_pos3d,16);
			return;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	
}
