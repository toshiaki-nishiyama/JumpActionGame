package jp.techacademy.toshiakinishiyama.jumpactiongame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by toshiaki.nishiyama on 2016/09/14.
 */
public class GameScreen extends ScreenAdapter
{
    // メンバ変数

    // カメラのサイズ（ディスプレイのサイズに依存しないサイズ）
    static final float CAMERA_WIDTH = 10;
    static final float CAMERA_HEIGHT = 15;

    // ゲーム世界の広さ
    static final float WORLD_WIDTH = 10;
    static final float WORLD_HEIGHT = 15 * 20; // 20画面分登れば終了

    // GUI 用のカメラのサイズ
    static final float GUI_WIDTH = 320;
    static final float GUI_HEIGHT = 480;

    // ゲームの状態
    static final int GAME_STATE_READY = 0;      // ゲーム開始前
    static final int GAME_STATE_PLAYING = 1;    // ゲーム中
    static final int GAME_STATE_GAMEOVER = 2;   // ゴールか、落下してゲーム終了

    // 重力
    static final float GRAVITY = -12;

    private JumpActionGame mGame;

    Sprite mBg;                      // コンピュータの処理の負荷を上げずに高速に画像を描画する仕組み
    OrthographicCamera mCamera;     // カメラ
    OrthographicCamera mGuiCamera; // GUI 用のカメラ

    FitViewport mViewPort;          // ビューポート
    FitViewport mGuiViewPort;      // GUI 用のビューポート

    Random mRandom;                 // 乱数を取得するためのクラス
    List<Step> mSteps;              // 生成して配置した 踏み台 を保持するリスト
    List<Star> mStars;              // 生成して配置した スター を保持するリスト
    Ufo mUfo;                       // 生成して配置した ゴール を保持する
    Player mPlayer;                 // 生成して配置した プレイヤー を保持する
    //List<Enemy> mEnemyList;        // 生成して配置した 敵キャラ を保持するリスト（減点ゲーム用）
    Enemy mEnemy;                   // 生成して配置した 敵キャラ を保持する（ゲームオーバー用）

    float mHeightSoFar;           // プレイヤーが地面からどれだけ離れたかを保持する
    int mGameState;                // ゲームの状態を保持する
    Vector3 mTouchPoint;           // タッチされた座標を保持する

    BitmapFont mFont;               // フォント
    int mScore;                    // スコア
    int mHighScore;               // ハイスコア
    Preferences mPrefs;            // データ永続化（キーと値でデータを保存する）

    Sound mSound;                   // 効果音

    public GameScreen(JumpActionGame game) {
        // 引数で受け取った JumpActionGame クラスをメンバ変数に保持
        mGame = game;

        // 背景の準備 　テクスチャはスプライトに張り付ける画像のこと
        Texture bgTexture = new Texture("back.png");
        // TextureRegionで切り出す時の原点は左上 　テクスチャリージョンはテクスチャとして用意した画像の一部を切り取ってスプライトに張り付けるためのもの
        mBg = new Sprite( new TextureRegion(bgTexture, 0, 0, 540, 810));
        mBg.setSize(CAMERA_WIDTH, CAMERA_HEIGHT);
        mBg.setPosition(0, 0);

        // カメラ、ViewPortを生成、設定する
        // カメラとビューポートの横、縦サイズを同一にすることにより、横縦比が固定される
        mCamera = new OrthographicCamera();
        mCamera.setToOrtho(false, CAMERA_WIDTH, CAMERA_HEIGHT);
        mViewPort = new FitViewport(CAMERA_WIDTH, CAMERA_HEIGHT, mCamera);

        // GUI 用のカメラを設定する
        mGuiCamera = new OrthographicCamera();
        mGuiCamera.setToOrtho(false, GUI_WIDTH, GUI_HEIGHT);
        mGuiViewPort = new FitViewport(GUI_WIDTH, GUI_HEIGHT, mGuiCamera);

        // メンバ変数の初期化
        mRandom = new Random();
        mSteps = new ArrayList<Step>();
        mStars = new ArrayList<Star>();
        //mEnemyList = new ArrayList<Enemy>();          // 減点ゲーム用
        mGameState = GAME_STATE_READY;
        mTouchPoint = new Vector3();
        mFont = new BitmapFont(Gdx.files.internal("font.fnt"), Gdx.files.internal("font.png"), false);
        mFont.getData().setScale(0.8f);
        mScore = 0;

        // 効果音の準備
        mSound = Gdx.audio.newSound(Gdx.files.internal("data/Hit02-1.mp3"));

        // ハイスコアを Preferences から取得する
        mPrefs = Gdx.app.getPreferences("jp.techacademy.toshiakinishiyama.jumpactiongame");
        mHighScore = mPrefs.getInteger("HIGHSCORE", 0);

        // オブジェクトを配置する
        createStage();
    }

    @Override
    public void render (float delta) {
        // それぞれの状態をアップデートする
        update(delta);

        // 画面を描画する準備（画面クリア）　⇒　glClearColor と glClear は 1セット
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // カメラの中心を超えたらカメラを上に移動させる　つまりキャラが画面の上半分には絶対に行かない
        if(mPlayer.getY() > mCamera.position.y)
        {
            mCamera.position.y = mPlayer.getY();
        }

        // カメラの座標をアップデート（計算）し、スプライトの表示に反映させる
        mCamera.update();
        mGame.batch.setProjectionMatrix(mCamera.combined);

        mGame.batch.begin();

        // 背景
        // 原点は左下
        mBg.setPosition(mCamera.position.x - CAMERA_WIDTH / 2, mCamera.position.y - CAMERA_HEIGHT / 2);
        mBg.draw(mGame.batch);      // スプライトなどの描画する場合は、begin と end の間で実行する

        // Step
        for (int i = 0; i < mSteps.size(); i++) {
            mSteps.get(i).draw(mGame.batch);
        }

        // Star
        for (int i = 0; i < mStars.size(); i++) {
            mStars.get(i).draw(mGame.batch);
        }

        // UFO
        mUfo.draw(mGame.batch);

        // 敵キャラ
        /* 減点ゲーム用
        for (int i = 0; i < mEnemyList.size(); i++) {
            mEnemyList.get(i).draw(mGame.batch);
        }
        */
        // ゲームオーバー用
        mEnemy.draw(mGame.batch);

        //Player
        mPlayer.draw(mGame.batch);

        mGame.batch.end();

        // スコア表示
        mGuiCamera.update();
        mGame.batch.setProjectionMatrix(mGuiCamera.combined);
        mGame.batch.begin();
        mFont.draw(mGame.batch, "HigScore: " + mHighScore, 16, GUI_HEIGHT - 15);
        mFont.draw(mGame.batch, "Score; " + mScore, 16, GUI_HEIGHT - 35);
        mGame.batch.end();
    }

    @Override
    public void resize(int width, int height)
    {
        mViewPort.update(width, height);
        mGuiViewPort.update(width, height);
    }

    // ステージを作成する
    private void createStage() {

        // テクスチャの準備
        Texture stepTexture = new Texture("step.png");
        Texture starTexture = new Texture("star.png");
        Texture playerTexture = new Texture("uma.png");
        Texture ufoTexture = new Texture("ufo.png");
        Texture enemyTexture = new Texture("enemy.png");

        // StepとStarをゴールの高さまで配置していく
        float y = 0;

        float maxJumpHeight = Player.PLAYER_JUMP_VELOCITY * Player.PLAYER_JUMP_VELOCITY / (2 * -GRAVITY);
        while (y < WORLD_HEIGHT - 5) {
            int type = mRandom.nextFloat() > 0.8f ? Step.STEP_TYPE_MOVING : Step.STEP_TYPE_STATIC;
            float x = mRandom.nextFloat() * (WORLD_WIDTH - Step.STEP_WIDTH);

            Step step = new Step(type, stepTexture, 0, 0, 144, 36);
            step.setPosition(x, y);
            mSteps.add(step);

            // スターを配置
            if (mRandom.nextFloat() > 0.6f) {
                Star star = new Star(starTexture, 0, 0, 72, 72);
                star.setPosition(step.getX() + mRandom.nextFloat(), step.getY() + Star.STAR_HEIGHT + mRandom.nextFloat() * 3);
                mStars.add(star);
            }

            /* 敵キャラを配置（減点ゲーム用）
            if (mRandom.nextFloat() > 0.6f) {
                Enemy enemy = new Enemy(enemyTexture, 0, 0, 72, 72);
                enemy.setPosition(step.getX() + mRandom.nextFloat(), step.getY() + Enemy.ENEMY_HEIGHT + mRandom.nextFloat() * 3);
                mEnemyList.add(enemy);
            }
            */

            y += (maxJumpHeight - 0.5f);
            y -= mRandom.nextFloat() * (maxJumpHeight / 3);
        }

        // Playerを配置
        mPlayer = new Player(playerTexture, 0, 0, 72, 72);
        mPlayer.setPosition(WORLD_WIDTH / 2 - mPlayer.getWidth() / 2, Step.STEP_HEIGHT);

        // 敵キャラを配置（ゲームオーバー用）
        mEnemy = new Enemy(enemyTexture, 0, 0, 72, 72);
        mEnemy.setPosition(WORLD_WIDTH / 2 - Enemy.ENEMY_WIDTH / 2, 50);

        // ゴールのUFOを配置
        mUfo = new Ufo(ufoTexture, 0, 0, 120, 74);
        mUfo.setPosition(WORLD_WIDTH / 2 - Ufo.UFO_WIDTH / 2, y);
    }

    // それぞれのオブジェクトの状態をアップデートする
    private void update(float delta) {
        switch (mGameState) {
            case GAME_STATE_READY:
                updateReady();
                break;
            case GAME_STATE_PLAYING:
                updatePlaying(delta);
                break;
            case GAME_STATE_GAMEOVER:
                updateGameOver();
                break;
        }
    }

    private void updateReady() {
        if (Gdx.input.justTouched()) {
            mGameState = GAME_STATE_PLAYING;
        }
    }

    private void updatePlaying(float delta) {
        float accel = 0;

        if (Gdx.input.isTouched())
        {
            mGuiViewPort.unproject(mTouchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0));
            Rectangle left = new Rectangle(0, 0, GUI_WIDTH / 2, GUI_HEIGHT);
            Rectangle right = new Rectangle(GUI_WIDTH / 2, 0, GUI_WIDTH / 2, GUI_HEIGHT);
            if (left.contains(mTouchPoint.x, mTouchPoint.y)) {
                accel = 5.0f;
            }
            if (right.contains(mTouchPoint.x, mTouchPoint.y)) {
                accel = -5.0f;
            }
        }

        // Step
        for (int i = 0; i < mSteps.size(); i++) {
            mSteps.get(i).update(delta);
        }

        // Player
        if (mPlayer.getY() <= 0.5f) {
            mPlayer.hitStep();
        }
        mPlayer.update(delta, accel);
        mHeightSoFar = Math.max(mPlayer.getY(), mHeightSoFar);

        // 当たり判定を行う
        checkCollision();

        // ゲームオーバーか判断する
        checkGameOver();
    }

    private void checkCollision()
    {
        // UFO（ゴールとの当たり判定）
        if(mPlayer.getBoundingRectangle().overlaps(mUfo.getBoundingRectangle()))
        {
            // ゴール
            mGameState = GAME_STATE_GAMEOVER;
            return;
        }

        // Star との当たり判定
        for(int i = 0; i < mStars.size(); i++)
        {
            Star star = mStars.get(i);

            if(star.mState == Star.STAR_NONE)
            {
                continue;
            }

            if(mPlayer.getBoundingRectangle().overlaps(star.getBoundingRectangle()))
            {
                star.get();
                mScore++;
                if(mScore > mHighScore)
                {
                    mHighScore = mScore;

                    // ハイスコアを Preference に保存する
                    mPrefs.putInteger("HIGHSCORE", mHighScore);
                    mPrefs.flush();
                }
                break;
            }
        }

        /* 敵キャラ との当たり判定（減点ゲーム用）
        for(int i = 0; i < mEnemyList.size(); i++)
        {
            Enemy enemy = mEnemy.get(i);

            if(enemy.mState == Enemy.ENEMY_NONE)
            {
                continue;
            }

            if(mPlayer.getBoundingRectangle().overlaps(enemy.getBoundingRectangle()))
            {
                enemy.get();
                // 効果音再生
                mSound.play(1.0F);
                // スコアを減点する
                mScore--;
                break;
            }
        }
        mSound.dispose();
        */

        // 敵キャラ との当たり判定（ゲームオーバー用）
        if(mPlayer.getBoundingRectangle().overlaps(mEnemy.getBoundingRectangle()))
        {
            // ゴール
            mSound.play(1.0F);
            mGameState = GAME_STATE_GAMEOVER;
            return;
        }

        // Step との当たり判定
        // 上昇中は Step との当たり判定を確認しない
        if(mPlayer.velocity.y > 0)
        {
            return;
        }

        for(int i = 0; i < mSteps.size(); i++)
        {
            Step step = mSteps.get(i);

            if(step.mState == Step.STEP_STATE_VANISH)
            {
                continue;
            }

            if(mPlayer.getY() > step.getY())
            {
                if(mPlayer.getBoundingRectangle().overlaps(step.getBoundingRectangle()))
                {
                    mPlayer.hitStep();
                    if(mRandom.nextFloat() > 0.5f)
                    {
                        step.vanish();
                    }
                    break;
                }
            }
        }
    }

    private void checkGameOver()
    {
        if(mHeightSoFar - CAMERA_HEIGHT / 2 > mPlayer.getY())
        {
            Gdx.app.log("JampActionGame", "GAMEOVER");
            mGameState = GAME_STATE_GAMEOVER;
        }
    }

    private void updateGameOver()
    {
        if (Gdx.input.justTouched())
        {
            mSound.dispose();
            mGame.setScreen(new ResultScreen(mGame, mScore));
        }
    }
}
