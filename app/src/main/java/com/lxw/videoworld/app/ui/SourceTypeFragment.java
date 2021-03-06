package com.lxw.videoworld.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.lxw.videoworld.R;
import com.lxw.videoworld.app.adapter.QuickFragmentPageAdapter;
import com.lxw.videoworld.app.api.HttpHelper;
import com.lxw.videoworld.app.config.Constant;
import com.lxw.videoworld.app.model.BaseResponse;
import com.lxw.videoworld.app.model.SelectorModel;
import com.lxw.videoworld.app.model.SourceDetailModel;
import com.lxw.videoworld.app.model.SourceListModel;
import com.lxw.videoworld.framework.base.BaseActivity;
import com.lxw.videoworld.framework.base.BaseFragment;
import com.lxw.videoworld.framework.http.HttpManager;
import com.lxw.videoworld.framework.image.ImageManager;
import com.lxw.videoworld.framework.log.LoggerHelper;
import com.lxw.videoworld.framework.util.SharePreferencesUtil;
import com.lxw.videoworld.framework.util.StringUtil;
import com.lxw.videoworld.framework.util.ValueUtil;
import com.lxw.videoworld.framework.widget.EmptyLoadMoreView;
import com.lxw.videoworld.framework.widget.MyHorizontalInfiniteCycleViewPager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import static com.lxw.videoworld.app.config.Constant.BANNER_LIMIT;

/**
 * 资源列表
 */
public class SourceTypeFragment extends BaseFragment {
    @BindView(R.id.recyclerview_source_type)
    RecyclerView recyclerviewSourceType;
    @BindView(R.id.refresh_source_type)
    SwipeRefreshLayout refreshSourceType;
    @BindView(R.id.recyclerview_selector)
    RecyclerView recyclerviewSelector;
    @BindView(R.id.drawerlayout_selector)
    DrawerLayout drawerlayoutSelector;
    Unbinder unbinder;
    @BindView(R.id.ll_content)
    LinearLayout llContent;
    MyHorizontalInfiniteCycleViewPager viewpagerBanner;
    /**
     * rootView是否初始化标志，防止回调函数在rootView为空的时候触发
     */
    private boolean hasCreateView;

    /**
     * 当前Fragment是否处于可见状态标志，防止因ViewPager的缓存机制而导致回调函数的触发
     */
    private boolean isFragmentVisible;
    private View rootView;
    private QuickFragmentPageAdapter<SourceBannerFragment> bannerAdapter;
    private List<SourceBannerFragment> sourceBannerFragments = new ArrayList<>();
    private SourceListModel sourceListModel;
    private List<SourceDetailModel> sourceDetails = new ArrayList<>();
    private List<SourceDetailModel> detailModels = new ArrayList<>();
    private BaseQuickAdapter<SourceDetailModel, BaseViewHolder> sourceAdapter;
    private BaseQuickAdapter.RequestLoadMoreListener loadMoreListener;
    private String sourceType;
    private String category;
    private String type;
    private String tab;
    private boolean frag_refresh = true;
    private boolean flag_init = true;// 首次加载
    private int page = 0;
    private int picWidth;
    private int picHeight;
    private boolean isVisibleToUser;

    private BaseQuickAdapter<SelectorModel, BaseViewHolder> selectorAdapter;
    private List<SelectorModel> selectors = new ArrayList<>();

    public SourceTypeFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initVariable();
        tab = getArguments().getString("tab");
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!hasCreateView && getUserVisibleHint()) {
            onFragmentVisibleChange(true);
            isFragmentVisible = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_source_type, null);
            unbinder = ButterKnife.bind(this, rootView);
            // 计算列表 Item 图片展示宽高 比例 4:3
            WindowManager wm = this.getActivity().getWindowManager();
            int width = wm.getDefaultDisplay().getWidth();
            final int height = wm.getDefaultDisplay().getHeight();
            picWidth = (width - ValueUtil.dip2px(getActivity(), recyclerviewSourceType.getPaddingLeft() + recyclerviewSourceType.getPaddingRight()) -
                    ValueUtil.dip2px(getActivity(), (Constant.GRIDLAYOUTMANAGER_SPANCOUNT - 1) * 10)) / Constant.GRIDLAYOUTMANAGER_SPANCOUNT;
            picHeight = picWidth * 4 / 3;
            // 下拉刷新
            refreshSourceType.setColorSchemeColors(((BaseActivity) getActivity()).getCustomColor(R.styleable.BaseColor_com_main_A));
            refreshSourceType.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    // TODO
                    page = 0;
                    frag_refresh = true;
                    sourceAdapter.setEnableLoadMore(true);
                    refreshSourceType.setRefreshing(false);
                    // 加载数据
                    getList(sourceType, category, type, "0", Constant.LIST_LIMIT + BANNER_LIMIT + "", true);

                }
            });
//            // banner
            viewpagerBanner = new MyHorizontalInfiniteCycleViewPager(getActivity());
            String key = sourceType + category;
            if (!TextUtils.isEmpty(type)) key = key + type;
            int id = SharePreferencesUtil.getIntSharePreferences(SourceTypeFragment.this.getContext(), key, -1);
            if (id == -1) {
                id = (int) (10000000 + Math.random() * 100000000);
                SharePreferencesUtil.setIntSharePreferences(SourceTypeFragment.this.getContext(), key, id);
            }
            viewpagerBanner.setId(id);
            int bannerHeight;
            bannerHeight = height / 2 + ValueUtil.dip2px(getActivity(), 10) + ValueUtil.sp2px(getActivity(), 20) * 2;
            viewpagerBanner.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bannerHeight));
            viewpagerBanner.setScrollDuration(3000);
            viewpagerBanner.setMediumScaled(true);
            viewpagerBanner.setMaxPageScale(0.8F);
            viewpagerBanner.setMinPageScale(0.5F);
            viewpagerBanner.setCenterPageScaleOffset(30.0F);
            viewpagerBanner.setMinPageScaleOffset(5.0F);
            viewpagerBanner.setOffscreenPageLimit(Constant.BANNER_LIMIT - 1);
//            viewpagerBanner.setOnInfiniteCyclePageTransformListener(...);
            recyclerviewSourceType.setLayoutManager(new GridLayoutManager(SourceTypeFragment.this.getActivity(), Constant.GRIDLAYOUTMANAGER_SPANCOUNT));
            // 列表适配器
            sourceAdapter = new BaseQuickAdapter<SourceDetailModel, BaseViewHolder>(R.layout.item_source_list, sourceDetails) {
                @Override
                protected void convert(BaseViewHolder helper, final SourceDetailModel item) {
                    // 设置列表 Item 图片、标题展示宽高
                    FrameLayout flPicture = ((FrameLayout) helper.getView(R.id.fl_picture));
                    flPicture.getLayoutParams().width = picWidth;
                    flPicture.getLayoutParams().height = picHeight;
                    TextView txtTitle = ((TextView) helper.getView(R.id.txt_title));
                    txtTitle.getLayoutParams().width = picWidth;
                    // 图片
                    List<String> images = ValueUtil.string2list(item.getImages());
                    if (images != null && images.size() > 0) {
                        ImageManager.getInstance().loadImage(SourceTypeFragment.this.getActivity(), (ImageView) helper.getView(R.id.img_picture), images.get(0));
                    }
                    // 标题
                    if (!TextUtils.isEmpty(item.getTitle())) {
                        helper.setText(R.id.txt_title, item.getTitle());
                    } else if (!TextUtils.isEmpty(item.getName()) && StringUtil.isHasChinese(item.getName())) {
                        helper.setText(R.id.txt_title, item.getName());
                    } else if (!TextUtils.isEmpty(item.getTranslateName())) {
                        helper.setText(R.id.txt_title, item.getTranslateName());
                    }
                    // 评分
                    if (!TextUtils.isEmpty(item.getImdbScore()) && item.getImdbScore().length() == 3) {
                        helper.setText(R.id.txt_imdb, item.getImdbScore());
                        helper.setVisible(R.id.ll_score, true);
                        helper.setVisible(R.id.ll_imdb, true);
                    } else {
                        helper.setVisible(R.id.ll_score, false);
                        helper.setVisible(R.id.ll_imdb, false);
                    }
                    if (!TextUtils.isEmpty(item.getDoubanScore()) && item.getDoubanScore().length() == 3) {
                        helper.setText(R.id.txt_douban, item.getDoubanScore());
                        helper.setVisible(R.id.ll_score, true);
                        helper.setVisible(R.id.ll_douban, true);
                    } else {
                        helper.setVisible(R.id.ll_score, false);
                        helper.setVisible(R.id.ll_douban, false);
                    }

                }
            };
            // item 点击事件
            sourceAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                    // TODO
                    Constant.detailModels = sourceAdapter.getData();
                    Intent intent = new Intent(SourceTypeFragment.this.getActivity(), SourceDetailActivity.class);
                    intent.putExtra("sourceDetailModel", (Serializable) adapter.getData().get(position));
                    startActivity(intent);
                }
            });
            // 加载更多
            loadMoreListener = new BaseQuickAdapter.RequestLoadMoreListener() {
                @Override
                public void onLoadMoreRequested() {
                    recyclerviewSourceType.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // TODO
                            frag_refresh = false;
                            sourceAdapter.setEnableLoadMore(false);
                            // 加载数据
                            getList(sourceType, category, type, Constant.LIST_LIMIT * page + BANNER_LIMIT + "", Constant.LIST_LIMIT + "", false);
                        }

                    }, 500);
                }
            };
            sourceAdapter.setOnLoadMoreListener(loadMoreListener, recyclerviewSourceType);
            // 当列表滑动到倒数第N个Item的时候(默认是1)回调onLoadMoreRequested方法
            sourceAdapter.setPreLoadNumber(7);
            sourceAdapter.setLoadMoreView(new EmptyLoadMoreView());
            // 动画
            sourceAdapter.openLoadAnimation();
            recyclerviewSourceType.setAdapter(sourceAdapter);
            // 筛选器
            createSelector();
            recyclerviewSelector.setLayoutManager(new LinearLayoutManager(getContext()));
            selectorAdapter = new BaseQuickAdapter<SelectorModel, BaseViewHolder>(R.layout.item_category_selector, selectors) {
                @Override
                protected void convert(BaseViewHolder helper, SelectorModel item) {
                    helper.setText(R.id.selector, item.getTitle());
                    helper.addOnClickListener(R.id.content);
                    if (item.isSelected())
                        helper.setTextColor(R.id.selector, getCustomColor(R.styleable.BaseColor_com_assist_A));
                    else
                        helper.setTextColor(R.id.selector, getCustomColor(R.styleable.BaseColor_com_font_A));
                }
            };
            selectorAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
                @Override
                public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                    if (drawerlayoutSelector.isDrawerOpen(GravityCompat.END)) {
                        drawerlayoutSelector.closeDrawers();
                    }
                    SelectorModel selectorModel = (SelectorModel) adapter.getData().get(position);
                    if (!selectorModel.isSelected()) {
                        sourceType = selectorModel.getSourceType();
                        category = selectorModel.getCategory();
                        type = selectorModel.getType();
                        for (int i = 0; i < adapter.getData().size(); i++) {
                            ((SelectorModel) adapter.getData().get(i)).setSelected(i == position);
                        }
                        selectorAdapter.notifyDataSetChanged();
                        sourceAdapter.getData().clear();
                        sourceAdapter.notifyDataSetChanged();
                        ViewGroup parent = (ViewGroup) viewpagerBanner.getParent();
                        if (parent != null) parent.removeView(viewpagerBanner);
                        sourceDetails.clear();
                        detailModels.clear();
                        getList(sourceType, category, type, "0", Constant.LIST_LIMIT + BANNER_LIMIT + "", true);
                    }
                }
            });
            recyclerviewSelector.setAdapter(selectorAdapter);
        }
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) {
                parent.removeView(rootView);
            }
        }

        return rootView;
    }

    public void createSelector() {
        // 筛选器
        selectors.clear();
        switch (Constant.SOURCE_TYPE) {
            case Constant.SOURCE_TYPE_1:
                switch (tab) {
                    case Constant.TAB_1:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_1, Constant.TAB_1, Constant.TYPE_0));
                        selectors.add(new SelectorModel(getString(R.string.txt_type1), Constant.SOURCE_TYPE_1, Constant.CATEGORY_1, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type2), Constant.SOURCE_TYPE_1, Constant.CATEGORY_2, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type3), Constant.SOURCE_TYPE_1, Constant.CATEGORY_3, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type4), Constant.SOURCE_TYPE_1, Constant.CATEGORY_4, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type5), Constant.SOURCE_TYPE_1, Constant.CATEGORY_5, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type6), Constant.SOURCE_TYPE_1, Constant.CATEGORY_6, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type7), Constant.SOURCE_TYPE_1, Constant.CATEGORY_7, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type8), Constant.SOURCE_TYPE_1, Constant.CATEGORY_8, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type9), Constant.SOURCE_TYPE_1, Constant.CATEGORY_9, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type10), Constant.SOURCE_TYPE_1, Constant.CATEGORY_10, null));
                        sourceType = Constant.SOURCE_TYPE_1;
                        category = Constant.TAB_1;
                        type = Constant.TYPE_0;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_2:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_1, Constant.CATEGORY_11, null));
                        sourceType = Constant.SOURCE_TYPE_1;
                        category = Constant.CATEGORY_11;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_3:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type17), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_6));
                        selectors.add(new SelectorModel(getString(R.string.txt_type18), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_7));
                        selectors.add(new SelectorModel(getString(R.string.txt_type19), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_9));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_19;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_4:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_1, Constant.CATEGORY_12, null));
                        sourceType = Constant.SOURCE_TYPE_1;
                        category = Constant.CATEGORY_12;
                        selectors.get(0).setSelected(true);
                        break;
                }
                break;
            case Constant.SOURCE_TYPE_2:
                switch (tab) {
                    case Constant.TAB_1:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_2, Constant.CATEGORY_14, null));
                        sourceType = Constant.SOURCE_TYPE_2;
                        category = Constant.CATEGORY_14;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_2:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_2, Constant.CATEGORY_15, null));
                        sourceType = Constant.SOURCE_TYPE_2;
                        category = Constant.CATEGORY_15;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_3:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type17), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_6));
                        selectors.add(new SelectorModel(getString(R.string.txt_type18), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_7));
                        selectors.add(new SelectorModel(getString(R.string.txt_type19), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_9));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_19;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_4:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_2, Constant.CATEGORY_16, null));
                        sourceType = Constant.SOURCE_TYPE_2;
                        category = Constant.CATEGORY_16;
                        selectors.get(0).setSelected(true);
                        break;
                }
                break;
            case Constant.SOURCE_TYPE_3:
                switch (tab) {
                    case Constant.TAB_1:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_17, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type16), Constant.SOURCE_TYPE_3, Constant.CATEGORY_17, Constant.TYPE_2));
                        selectors.add(new SelectorModel(getString(R.string.txt_type14), Constant.SOURCE_TYPE_3, Constant.CATEGORY_17, Constant.TYPE_1));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_17;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_2:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_18, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type11), Constant.SOURCE_TYPE_3, Constant.CATEGORY_18, Constant.TYPE_3));
                        selectors.add(new SelectorModel(getString(R.string.txt_type12), Constant.SOURCE_TYPE_3, Constant.CATEGORY_18, Constant.TYPE_5));
                        selectors.add(new SelectorModel(getString(R.string.txt_type13), Constant.SOURCE_TYPE_3, Constant.CATEGORY_18, Constant.TYPE_4));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_18;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_3:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type17), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_6));
                        selectors.add(new SelectorModel(getString(R.string.txt_type18), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_7));
                        selectors.add(new SelectorModel(getString(R.string.txt_type19), Constant.SOURCE_TYPE_3, Constant.CATEGORY_19, Constant.TYPE_9));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_19;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_4:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_3, Constant.CATEGORY_20, null));
                        sourceType = Constant.SOURCE_TYPE_3;
                        category = Constant.CATEGORY_20;
                        selectors.get(0).setSelected(true);
                        break;
                }
                break;
            case Constant.SOURCE_TYPE_4:
                switch (tab) {
                    case Constant.TAB_1:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type1), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_12));
                        selectors.add(new SelectorModel(getString(R.string.txt_type2), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_13));
                        selectors.add(new SelectorModel(getString(R.string.txt_type5), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_14));
                        selectors.add(new SelectorModel(getString(R.string.txt_type3), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_15));
                        selectors.add(new SelectorModel(getString(R.string.txt_type4), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_16));
                        selectors.add(new SelectorModel(getString(R.string.txt_type8), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_17));
                        selectors.add(new SelectorModel(getString(R.string.txt_type9), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_18));
                        selectors.add(new SelectorModel(getString(R.string.txt_type20), Constant.SOURCE_TYPE_4, Constant.CATEGORY_14, Constant.TYPE_19));
                        selectors.add(new SelectorModel(getString(R.string.txt_type21), Constant.SOURCE_TYPE_4, Constant.CATEGORY_24, Constant.TYPE_20));
                        selectors.add(new SelectorModel("", Constant.SOURCE_TYPE_4, Constant.CATEGORY_24, Constant.TYPE_28));
                        sourceType = Constant.SOURCE_TYPE_4;
                        category = Constant.CATEGORY_14;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_2:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, null));
                        selectors.add(new SelectorModel(getString(R.string.txt_type22), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_21));
                        selectors.add(new SelectorModel(getString(R.string.txt_type23), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_22));
                        selectors.add(new SelectorModel(getString(R.string.txt_type24), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_23));
                        selectors.add(new SelectorModel(getString(R.string.txt_type25), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_24));
                        selectors.add(new SelectorModel(getString(R.string.txt_type26), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_25));
                        selectors.add(new SelectorModel(getString(R.string.txt_type27), Constant.SOURCE_TYPE_4, Constant.CATEGORY_15, Constant.TYPE_26));
                        sourceType = Constant.SOURCE_TYPE_4;
                        category = Constant.CATEGORY_15;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_3:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_4, Constant.CATEGORY_23, null));
                        sourceType = Constant.SOURCE_TYPE_4;
                        category = Constant.CATEGORY_23;
                        selectors.get(0).setSelected(true);
                        break;
                    case Constant.TAB_4:
                        selectors.add(new SelectorModel(getString(R.string.txt_type0), Constant.SOURCE_TYPE_4, Constant.CATEGORY_16, null));
                        sourceType = Constant.SOURCE_TYPE_4;
                        category = Constant.CATEGORY_16;
                        selectors.get(0).setSelected(true);
                        break;
                }
                break;
        }
    }

    public void getList(String sourceType, String category, String type, String start, String limit, boolean flag_dialog) {
        LoggerHelper.info("SourceTypeFragment", start + " + " + limit);
        new HttpManager<SourceListModel>((BaseActivity) SourceTypeFragment.this.getActivity(), HttpHelper.getInstance().getList(sourceType, category, type, start, limit), flag_dialog, true) {

            @Override
            public void onSuccess(BaseResponse<SourceListModel> response) {
                sourceListModel = response.getResult();

                if (sourceListModel != null && sourceListModel.getList() != null && sourceListModel.getList().size() > 0) {
                    page++;
                    List<SourceDetailModel> sources = sourceListModel.getList();
                    if (frag_refresh) {
                        detailModels.clear();
                        detailModels.addAll(sources);
                        if (isVisibleToUser) Constant.detailModels = detailModels;
                        if (sources.size() >= Constant.BANNER_LIMIT) {
                            sourceDetails.clear();
                            List<SourceDetailModel> bannerDetails = new ArrayList<>();
                            for (int j = 0; j < Constant.BANNER_LIMIT; j++) {
                                bannerDetails.add(sources.get(j));
                            }
                            // banner 数据
                            if (sourceBannerFragments.size() == 0) {
                                 for (int i = 0; i < Constant.BANNER_LIMIT; i++) {
                                    SourceBannerFragment fragment = new SourceBannerFragment();
                                    Bundle bundle = new Bundle();
                                    bundle.putSerializable("item", (Serializable) sources.get(i));
                                     fragment.setSourceDetails(bannerDetails);
                                    fragment.setArguments(bundle);
                                    sourceBannerFragments.add(fragment);
                                }
                            } else {
                                for (int i = 0; i < Constant.BANNER_LIMIT; i++) {
                                    if (sourceBannerFragments.get(i) != null) {
                                        sourceBannerFragments.get(i).setSourceDetails(bannerDetails);
                                        sourceBannerFragments.get(i).refreshUI(sources.get(i));
                                    }
                                }
                            }

                            // banner 初始化
                            if (sourceBannerFragments.size() > 0) {
                                if (bannerAdapter == null) {
                                    if (SourceTypeFragment.this.getParentFragment() != null) {
                                        bannerAdapter = new QuickFragmentPageAdapter(SourceTypeFragment.this.getParentFragment().getFragmentManager(), sourceBannerFragments, new String[sourceBannerFragments.size()]);
                                    } else
                                        bannerAdapter = new QuickFragmentPageAdapter(SourceTypeFragment.this.getChildFragmentManager(), sourceBannerFragments, new String[sourceBannerFragments.size()]);
                                    viewpagerBanner.setOffscreenPageLimit(Constant.BANNER_LIMIT - 1);
                                    viewpagerBanner.setAdapter(bannerAdapter);
                                    viewpagerBanner.startAutoScroll(true);
                                }
                                if (sourceAdapter.getHeaderLayoutCount() == 0) {
                                    ViewGroup parent = (ViewGroup) viewpagerBanner.getParent();
                                    if (parent != null) {
                                        parent.removeView(viewpagerBanner);
                                    }
                                    sourceAdapter.addHeaderView(viewpagerBanner);
                                }
                            }
                        }
                        // 列表数据
                        for (int j = Constant.BANNER_LIMIT; j < sources.size(); j++) {
                            sourceDetails.add(sources.get(j));
                        }
                        sourceAdapter.setNewData(sourceDetails);
                        llContent.setVisibility(View.VISIBLE);
                    } else {
                        detailModels.addAll(sources);
                        if (isVisibleToUser) Constant.detailModels = detailModels;
                        sourceAdapter.addData(sources);
                        sourceAdapter.loadMoreComplete();
                    }
                } else {
                    sourceAdapter.loadMoreFail();
                }
                sourceAdapter.setEnableLoadMore(true);
                sourceAdapter.setOnLoadMoreListener(loadMoreListener, recyclerviewSourceType);
            }

            @Override
            public void onFailure(BaseResponse<SourceListModel> response) {
                sourceAdapter.loadMoreFail();
                sourceAdapter.setEnableLoadMore(true);
                sourceAdapter.setOnLoadMoreListener(loadMoreListener, recyclerviewSourceType);
            }
        }.doRequest();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void initVariable() {
        hasCreateView = false;
        isFragmentVisible = false;
    }


    public void setUpDrawerLayout() {
        if (drawerlayoutSelector != null) {
            if (drawerlayoutSelector.isDrawerOpen(GravityCompat.END)) {
                drawerlayoutSelector.closeDrawers();
            } else drawerlayoutSelector.openDrawer(GravityCompat.END);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        this.isVisibleToUser = isVisibleToUser;
        if (rootView == null) {
            return;
        }
        hasCreateView = true;
        if (isVisibleToUser) {
            onFragmentVisibleChange(true);
            isFragmentVisible = true;
            Constant.detailModels = detailModels;
            return;
        } else {
            if (drawerlayoutSelector.isDrawerOpen(GravityCompat.END)) {
                drawerlayoutSelector.closeDrawers();
            }
        }
        if (isFragmentVisible) {
            onFragmentVisibleChange(false);
            isFragmentVisible = false;
        }
    }

    protected void onFragmentVisibleChange(boolean isVisible) {
        if (flag_init && isVisible && !TextUtils.isEmpty(sourceType) && !TextUtils.isEmpty(category)) {
            // 加载数据
            flag_init = false;
            getList(sourceType, category, type, "0", Constant.LIST_LIMIT + BANNER_LIMIT + "", true);
        }
    }
}
