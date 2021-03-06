package cardchanneler.orbs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.megacrit.cardcrawl.actions.utility.ShowCardAction;
import com.megacrit.cardcrawl.actions.utility.ShowCardAndPoofAction;
import com.megacrit.cardcrawl.actions.utility.UseCardAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.AbstractCard.CardTarget;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.cards.SoulGroup;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.localization.OrbStrings;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import com.megacrit.cardcrawl.vfx.combat.*;

import basemod.BaseMod;
import basemod.abstracts.DynamicVariable;
import cardchanneler.helpers.OrbTargettingStraightArrow;
import cardchanneler.patches.XCostEvokePatch;
import cardchanneler.patches.ChanneledCardDiscardPatch.BeingRetainedAsOrbField;
import cardchanneler.vfx.ChanneledCardPassiveEffect;

public class ChanneledCard extends AbstractOrb {

    // Standard ID/Description
    public static final String ORB_ID = "CardChanneler:ChanneledCard";
    private static final OrbStrings orbString = CardCrawlGame.languagePack.getOrbString(ORB_ID);

    // Animation Rendering Numbers
    public static final float scale = 0.2f;
    private float vfxTimer = 1.0f;
    private float vfxIntervalMin = 0.025f;
    private float vfxIntervalMax = 0.1f;

    public AbstractCard card = null;
    
    //beingEvoked tells to CardEvokationIgnoresThornsPatch to
    //prevent thorns from hurting you because an orb is doing the damage.
    public static boolean beingEvoked = false;
    
    //orbBeingLost tells ChanneledCardDiscardPatch to not let
    //cards go to the discard or draw pile if they will return
    public static boolean orbBeingLost = false;
    public AbstractMonster monsterTarget;

    public ChanneledCard(AbstractCard card) {
        super();
        ID = ORB_ID;
//        card.exhaust = false;
        card.setAngle(0, true);
        this.card = card;
        monsterTarget = AbstractDungeon.getRandomMonster();
        name = orbString.NAME + " " + card.name;
        updateDescription();
    }

    private String getDynamicValue(final String key) {
        String value = null;
        DynamicVariable dv = BaseMod.cardDynamicVariableMap.get(key);
        if (dv != null) {
            if (dv.isModified(card)) {
                if (dv.value(card) >= dv.baseValue(card)) {
                    value = "[#" + dv.getIncreasedValueColor().toString() + "]" + Integer.toString(dv.value(card)) + "[]";
                } else {
                    value = "[#" + dv.getDecreasedValueColor().toString() + "]" + Integer.toString(dv.value(card)) + "[]";
                }
            } else {
                value = Integer.toString(dv.baseValue(card));
            }
        }
        return (String) value;
    }

    // Set the on-hover description of the orb
    @Override
    public void updateDescription() { 
        description = orbString.DESCRIPTION[0];
        boolean firstWord = false;
        card.initializeDescription();
        String descriptionFragment = "";
        for (int i=0; i<card.description.size(); i++){
            descriptionFragment = card.description.get(i).getText();
            for (String word : descriptionFragment.split(" ")) {
                if (firstWord){
                    firstWord = false;
                }else{
                    description += " ";
                }
                if (word.length() > 0 && word.charAt(0) == '*') {
                    word = word.substring(1);
                    String punctuation = "";
                    if (word.length() > 1 && !Character.isLetter(word.charAt(word.length() - 2))) {
                        punctuation += word.charAt(word.length() - 2);
                        word = word.substring(0, word.length() - 2);
                        punctuation += ' ';
                    }
                    description += word;
                    description += punctuation;
                }
                else if (word.length() > 0 && word.charAt(0) == '!') {
                    String key = "";
                    for (int j=1; j<word.length(); j++){
                        if (word.charAt(j) == '!'){
                            description += getDynamicValue(key);
                            description += word.substring(j+1);
                        }
                        else {
                            key += word.charAt(j);
                        }
                    }
                }
                else{
                    description += word;
                }
            }
        }
    }

    @Override
    public void applyFocus() {
        //Not affected by focus
    }

    @Override
    public void onEvoke() {
        if (monsterTarget.isDeadOrEscaped()){
            //Let the orb have an effect, even if the player forgot to pick a
            //new target
            monsterTarget = AbstractDungeon.getRandomMonster();
        }
        card.unhover();
        card.untip();
        card.stopGlowing();
        beingEvoked = true;
        if (card.cost > 0) {
        	card.freeToPlayOnce = true;
        }
        
        //Special code required to handle when the player's energy is used for X cost cards
    	System.out.println("panel was " + EnergyPanel.getCurrentEnergy());
    	if (XCostEvokePatch.oldEnergyValue == XCostEvokePatch.DEFAULT_ENERGY_VALUE) {
    		XCostEvokePatch.oldEnergyValue = EnergyPanel.getCurrentEnergy();
    	}
        System.out.println("Setting panel to " + XCostEvokePatch.CostAtChannelField.costAtChannel.get(card));
        EnergyPanel.setEnergy(XCostEvokePatch.CostAtChannelField.costAtChannel.get(card));
        card.energyOnUse = XCostEvokePatch.CostAtChannelField.costAtChannel.get(card);
        
        card.calculateCardDamage(monsterTarget);
        card.use(AbstractDungeon.player, monsterTarget);
        
        //Skipping this because these they apply to curses, which aren't channeled anyways
//            for (final AbstractPower p : AbstractDungeon.player.powers) {
//                if (!targetCard.dontTriggerOnUseCard) {
//                    p.onAfterUseCard(targetCard, this);
//                }
//            }
//            for (final AbstractMonster m : AbstractDungeon.getMonsters().monsters) {
//                for (final AbstractPower p2 : m.powers) {
//                    if (!targetCard.dontTriggerOnUseCard) {
//                        p2.onAfterUseCard(targetCard, this);
//                    }
//                }
//            }
            card.freeToPlayOnce = false;
            if (card.purgeOnUse && !BeingRetainedAsOrbField.beingRetainedAsOrb.get(card)) {
                AbstractDungeon.actionManager.addToTop(new ShowCardAndPoofAction(card));
                AbstractDungeon.player.cardInUse = null;
                return;
            }
            
            if (card.type == AbstractCard.CardType.POWER) {
            	//skip the animation that pro
//                AbstractDungeon.actionManager.addToTop(new ShowCardAction(targetCard));
//                if (Settings.FAST_MODE) {
//                    AbstractDungeon.actionManager.addToTop(new WaitAction(0.1f));
//                }
//                else {
//                    AbstractDungeon.actionManager.addToTop(new WaitAction(0.7f));
//                }
                AbstractDungeon.player.hand.empower(card);
                AbstractDungeon.player.hand.applyPowers();
                AbstractDungeon.player.hand.glowCheck();
//                AbstractDungeon.player.cardInUse = null;
                return;
            }
            AbstractDungeon.player.cardInUse = null;
            boolean exhaustCard = card.exhaustOnUseOnce || card.exhaust;
            
            if (!exhaustCard) {
//                if (this.reboundCard) {
            	//Skip rebounds because they imply that the card is being played
//                    AbstractDungeon.player.hand.moveToDeck(targetCard, false);
//                }
//                else {
	                AbstractDungeon.player.discardPile.addToTop(card);
	                //discard
//                }
            }
            else { 
                card.exhaustOnUseOnce = false;
                if (AbstractDungeon.player.hasRelic("Strange Spoon") && card.type != AbstractCard.CardType.POWER) {
                    if (AbstractDungeon.cardRandomRng.randomBoolean()) {
                        AbstractDungeon.player.getRelic("Strange Spoon").flash();
                        AbstractDungeon.player.hand.moveToDiscardPile(card);
                    }
                    else {
                    	AbstractDungeon.player.exhaustPile.addToTop(card);
                        CardCrawlGame.dungeon.checkForPactAchievement();
                    }
                }
                else {
                	AbstractDungeon.player.exhaustPile.addToTop(card);
                    CardCrawlGame.dungeon.checkForPactAchievement();
                }
            }
            //dontTriggerOnUseCard applies to curses, which aren't channeled anyways
//            if (targetCard.dontTriggerOnUseCard) { 
//                targetCard.dontTriggerOnUseCard = false;
//            }
//        AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, monsterTarget, card.energyOnUse, true));
//        AbstractDungeon.actionManager.addToTop(new UseCardAction(card, monsterTarget));
    }

    @Override
    public void onStartOfTurn() {
        //No passive effect
    }

    @Override
    public void updateAnimation() {
        super.updateAnimation();
        vfxTimer -= Gdx.graphics.getDeltaTime();
        if (this.vfxTimer < 0.0f) {
            AbstractDungeon.effectList.add(new ChanneledCardPassiveEffect(this.cX, this.cY));
            this.vfxTimer = MathUtils.random(this.vfxIntervalMin, this.vfxIntervalMax);
        }
    }

    //Related to the Disciple mod's switch card tip rendering:
    //https://github.com/Tempus/The-Disciple/blob/master/src/main/java/cards/switchCards/AbstractSelfSwitchCard.java
    @Override
    public void render(SpriteBatch sb) {
        card.current_x = cX;
        card.current_y = cY;
        card.drawScale = scale;
        card.render(sb);
        hb.render(sb);
        if ((card.target == CardTarget.ENEMY || card.target == CardTarget.SELF_AND_ENEMY) &&
        		monsterTarget != null &&
        		monsterTarget.hb != null){
            sb.end();
            OrbTargettingStraightArrow.drawArrow(this, monsterTarget);
            sb.begin();
            card.renderCardTip(sb);
        }
    }

    @Override
    public void triggerEvokeAnimation() {
        AbstractDungeon.effectsQueue.add(new DarkOrbActivateEffect(this.cX, this.cY));
    }

    @Override
    public void playChannelSFX() {
        //Just use the card's SFX
    }

    @Override
    public AbstractOrb makeCopy() {
        return new ChanneledCard(this.card);
    }
}
