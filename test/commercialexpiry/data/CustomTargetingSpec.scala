package commercialexpiry.data

import com.google.api.ads.dfp.axis.v201505.CustomCriteriaComparisonOperator.{IS, IS_NOT}
import com.google.api.ads.dfp.axis.v201505.CustomCriteriaSetLogicalOperator.AND
import com.google.api.ads.dfp.axis.v201505.{CustomCriteria, CustomCriteriaSet}
import commercialexpiry.Config.dfp.{adFeatureSlotTargetValueId, keywordTargetKeyId, seriesTargetKeyId, slotTargetKeyId}
import org.specs2.mutable._

class CustomTargetingSpec extends Specification {

  private def seriesTargetValue: (Long) => Some[String] = { _ => Some("series") }

  private def keywordTargetValue: (Long) => Some[String] = { _ => Some("keyword") }

  "adFeatureTagTarget" should {

    "respect positive targeting" in {
      val slotCriterion = new CustomCriteria(slotTargetKeyId, Array(adFeatureSlotTargetValueId), IS)
      val seriesCriterion = new CustomCriteria(seriesTargetKeyId, Array(1), IS_NOT)
      val keywordCriterion = new CustomCriteria(keywordTargetKeyId, Array(2), IS)
      val criteriaSet = new CustomCriteriaSet(AND,
        Array(slotCriterion, seriesCriterion, keywordCriterion))

      val keywordTarget =
        CustomTargeting.adFeatureTagTarget(criteriaSet,
          targetKeyId = keywordTargetKeyId,
          targetValue = keywordTargetValue)

      keywordTarget must beSome("keyword")
    }

    "ignore negative targeting" in {
      val slotCriterion = new CustomCriteria(slotTargetKeyId, Array(adFeatureSlotTargetValueId), IS)
      val seriesCriterion = new CustomCriteria(seriesTargetKeyId, Array(1), IS_NOT)
      val keywordCriterion = new CustomCriteria(keywordTargetKeyId, Array(2), IS)
      val criteriaSet = new CustomCriteriaSet(AND,
        Array(slotCriterion, seriesCriterion, keywordCriterion))

      val seriesTarget =
        CustomTargeting.adFeatureTagTarget(criteriaSet,
          targetKeyId = seriesTargetKeyId,
          targetValue = seriesTargetValue)

      seriesTarget must beNone
    }
  }
}
