package de.metas.contracts.interceptor;

/*
 * #%L
 * de.metas.contracts
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

import org.adempiere.ad.modelvalidator.annotations.DocValidate;
import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.FillMandatoryException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.model.ModelValidator;

import com.google.common.collect.ImmutableList;

import de.metas.contracts.IFlatrateBL;
import de.metas.contracts.IFlatrateDAO;
import de.metas.contracts.model.I_C_Flatrate_Conditions;
import de.metas.contracts.model.I_C_Flatrate_DataEntry;
import de.metas.contracts.model.I_C_Flatrate_Term;
import de.metas.contracts.model.I_C_Invoice_Clearing_Alloc;
import de.metas.contracts.model.X_C_Flatrate_Conditions;
import de.metas.contracts.model.X_C_Flatrate_DataEntry;
import de.metas.i18n.IMsgBL;
import de.metas.invoicecandidate.model.I_C_Invoice_Candidate;

@Interceptor(I_C_Flatrate_DataEntry.class)
public class C_Flatrate_DataEntry
{

	private static final String MSG_DATA_ENTRY_ALREADY_INVOICED_0P = "DataEntry_Already_Invoiced";
	private static final String MSG_DATA_ENTRY_EXISTING_CORRECTION_ENTRY_0P = "DataEntry_Existing_Correction_Entry";
	public static final String MSG_DATA_ENTRY_EXISTING_CLOSING_ENTRY_0P = "DataEntry_Existing_Closing_Entry";

	final transient IFlatrateBL flatrateBL = Services.get(IFlatrateBL.class);
	final transient IFlatrateDAO flatrateDB = Services.get(IFlatrateDAO.class);

	public static final transient C_Flatrate_DataEntry instance = new C_Flatrate_DataEntry();

	private static final List<String> DATAENTRY_TYPES_InvoiceCandidatesRelated = ImmutableList
			.of(X_C_Flatrate_DataEntry.TYPE_Correction_PeriodBased, X_C_Flatrate_DataEntry.TYPE_Invoicing_PeriodBased);	

	private C_Flatrate_DataEntry()
	{
	}

	@ModelChange(timings = { ModelValidator.TYPE_BEFORE_NEW, ModelValidator.TYPE_BEFORE_CHANGE }, ifColumnsChanged = { I_C_Flatrate_DataEntry.COLUMNNAME_ActualQty, I_C_Flatrate_DataEntry.COLUMNNAME_Qty_Reported })
	public void updateDataEntry(final I_C_Flatrate_DataEntry dataEntry)
	{
		final I_C_Flatrate_Term term = dataEntry.getC_Flatrate_Term();

		if (term.getC_Flatrate_Conditions_ID() < 0 || term.getC_UOM_ID() != dataEntry.getC_UOM_ID())
		{
			return; // nothing to do
		}

		final I_C_Flatrate_Conditions conditions = term.getC_Flatrate_Conditions();

		if (!X_C_Flatrate_Conditions.TYPE_CONDITIONS_Refundable.equals(conditions.getType_Conditions()))
		{
			flatrateBL.updateEntry(dataEntry);
		}

	}

	@ModelChange(timings = { ModelValidator.TYPE_AFTER_NEW })
	public void setDataEntry(final I_C_Flatrate_DataEntry dataEntry)
	{
		final I_C_Flatrate_Term term = dataEntry.getC_Flatrate_Term();

		if (term.getC_Flatrate_Conditions_ID() < 0 || term.getC_UOM_ID() != dataEntry.getC_UOM_ID())
		{
			return; // nothing to do
		}

		final boolean mainEntry = dataEntry.getC_UOM_ID() == term.getC_UOM_ID();

		if (mainEntry && !dataEntry.isSimulation() && isInvoiceCandidatesRelatedType(dataEntry))
		{
			// handle open clearing allocs
			final List<I_C_Invoice_Clearing_Alloc> allocsWithoutDataEntry = flatrateDB.retrieveOpenClearingAllocs(dataEntry);

			for (final I_C_Invoice_Clearing_Alloc ica : allocsWithoutDataEntry)
			{
				ica.setC_Flatrate_DataEntry_ID(dataEntry.getC_Flatrate_DataEntry_ID());
				InterfaceWrapperHelper.save(ica);
			}

			// handle invoice candidates that have no clearing allocs
			flatrateDB.updateCandidates(dataEntry);
		}
	}

	/** @return true if dataEntry's type is invoice candidates related */
	private final boolean isInvoiceCandidatesRelatedType(I_C_Flatrate_DataEntry dataEntry)
	{
		final String dataEntryType = dataEntry.getType();
		return DATAENTRY_TYPES_InvoiceCandidatesRelated.contains(dataEntryType);
	}

	@ModelChange(timings = { ModelValidator.TYPE_AFTER_NEW, ModelValidator.TYPE_AFTER_CHANGE }, ifColumnsChanged = { I_C_Flatrate_DataEntry.COLUMNNAME_ActualQty })
	public void updateQtyActualFromDataEntry(final I_C_Flatrate_DataEntry dataEntry)
	{
		final I_C_Flatrate_Term term = dataEntry.getC_Flatrate_Term();

		if (term.getC_Flatrate_Conditions_ID() < 0 || term.getC_UOM_ID() != dataEntry.getC_UOM_ID())
		{
			return; // nothing to do
		}

		final boolean mainEntry = dataEntry.getC_UOM_ID() == term.getC_UOM_ID();

		if (dataEntry.isSimulation() || mainEntry)
		{
			flatrateDB.updateQtyActualFromDataEntry(dataEntry);
		}
	}

	@ModelChange(timings = { ModelValidator.TYPE_AFTER_DELETE })
	public void clearInvoiceAllocsDatEntry(final I_C_Flatrate_DataEntry dataEntry)
	{
		final I_C_Flatrate_Term term = dataEntry.getC_Flatrate_Term();

		if (term.getC_Flatrate_Conditions_ID() < 0 || term.getC_UOM_ID() != dataEntry.getC_UOM_ID())
		{
			return; // nothing to do
		}

		if (isInvoiceCandidatesRelatedType(dataEntry))
		{
			final List<I_C_Invoice_Clearing_Alloc> allocsOfDataEntry = flatrateDB.retrieveClearingAllocs(dataEntry);
			for (final I_C_Invoice_Clearing_Alloc ica : allocsOfDataEntry)
			{
				ica.setC_Flatrate_DataEntry_ID(0);
				ica.setC_Invoice_Candidate_ID(0);
				InterfaceWrapperHelper.save(ica);
			}
		}
	}

	@DocValidate(timings = { ModelValidator.TIMING_BEFORE_VOID, ModelValidator.TIMING_BEFORE_CLOSE })
	public void disallowNotSupportedDocActions(final I_C_Flatrate_DataEntry dataEntry)
	{
		final Properties ctx = InterfaceWrapperHelper.getCtx(dataEntry);
		final String msg = Services.get(IMsgBL.class).getMsg(ctx, MainValidator.MSG_FLATRATE_DOC_ACTION_NOT_SUPPORTED_0P);
		throw new AdempiereException(msg);
	}

	@DocValidate(timings = { ModelValidator.TIMING_BEFORE_PREPARE })
	public void assertMandatoryQtyReported(final I_C_Flatrate_DataEntry dataEntry)
	{
		if (dataEntry.getQty_Reported() == null)
		{
			throw new FillMandatoryException(I_C_Flatrate_DataEntry.COLUMNNAME_Qty_Reported);
		}
	}

	@DocValidate(timings = { ModelValidator.TIMING_BEFORE_COMPLETE })
	public void beforeCompleteDataEntry(final I_C_Flatrate_DataEntry dataEntry)
	{
		if (dataEntry.isSimulation())
		{
			return;
		}

		flatrateBL.beforeCompleteDataEntry(dataEntry);
	}

	@DocValidate(timings = { ModelValidator.TIMING_BEFORE_REACTIVATE })
	public void validateBeforeReactivate(final I_C_Flatrate_DataEntry dataEntry)
	{
		if (dataEntry.isSimulation())
		{
			return;
		}

		beforeReactivate(dataEntry);
	}

	@DocValidate(timings = { ModelValidator.TIMING_AFTER_REACTIVATE })
	public void validateAfterReactivate(final I_C_Flatrate_DataEntry dataEntry)
	{
		if (dataEntry.isSimulation())
		{
			return;
		}

		afterReactivate(dataEntry);
	}
	
	private void beforeReactivate(final I_C_Flatrate_DataEntry dataEntry)
	{
		final String trxName = InterfaceWrapperHelper.getTrxName(dataEntry);

		// We need this assumption, because we are going to delete a C_Invoice_Candidate and that needs to happen in the
		// same trx in which we set dataEntry's C_Invoice_Candidate_ID to 0
		// (otherwise the DB's FK constraints wouldn't allow it).
		Check.assume(trxName != null, dataEntry + " has a trxName!=null");

		final I_C_Invoice_Candidate invoiceCandidate = dataEntry.getC_Invoice_Candidate();
		if (invoiceCandidate != null && invoiceCandidate.getQtyInvoiced().signum() != 0)
		{
			throw new AdempiereException("@" + MSG_DATA_ENTRY_ALREADY_INVOICED_0P + "@");
		}

		final I_C_Invoice_Candidate icCorr = dataEntry.getC_Invoice_Candidate_Corr();
		if (icCorr != null && icCorr.getQtyInvoiced().signum() != 0)
		{
			throw new AdempiereException("@" + MSG_DATA_ENTRY_ALREADY_INVOICED_0P + "@");
		}

		final I_C_Flatrate_Term flatrateTerm = dataEntry.getC_Flatrate_Term();
		final IFlatrateDAO flatrateDB = Services.get(IFlatrateDAO.class);

		//
		if (X_C_Flatrate_DataEntry.TYPE_Invoicing_PeriodBased.equals(dataEntry.getType()))
		{
			if (flatrateTerm.isClosingWithCorrectionSum())
			{
				final Timestamp dataEntryStartDate = dataEntry.getC_Period().getStartDate();

				final List<I_C_Flatrate_DataEntry> existingCorrectionEntries = flatrateDB.retrieveDataEntries(
						flatrateTerm,
						X_C_Flatrate_DataEntry.TYPE_Correction_PeriodBased,
						dataEntry.getC_UOM());
				for (final I_C_Flatrate_DataEntry corrEntry : existingCorrectionEntries)
				{
					final Timestamp corrEntryStartDate = corrEntry.getC_Period().getStartDate();
					if (corrEntryStartDate.equals(dataEntryStartDate) || corrEntryStartDate.after(dataEntryStartDate))
					{
						throw new AdempiereException("@" + MSG_DATA_ENTRY_EXISTING_CORRECTION_ENTRY_0P + "@");
					}
				}
			}
		}

		if (invoiceCandidate != null)
		{
			dataEntry.setC_Invoice_Candidate_ID(0);
			InterfaceWrapperHelper.delete(invoiceCandidate);
		}

		if (icCorr != null)
		{
			dataEntry.setC_Invoice_Candidate_Corr_ID(0);
			InterfaceWrapperHelper.delete(icCorr);
		}

	}
	
	private void afterReactivate(final I_C_Flatrate_DataEntry dataEntry)
	{
		if (X_C_Flatrate_DataEntry.TYPE_Invoicing_PeriodBased.equals(dataEntry.getType()))
		{
			final List<I_C_Invoice_Clearing_Alloc> allocLines = flatrateDB.retrieveClearingAllocs(dataEntry);
			for (final I_C_Invoice_Clearing_Alloc alloc : allocLines)
			{
				final I_C_Invoice_Candidate candToClear = alloc.getC_Invoice_Cand_ToClear();
				candToClear.setQtyInvoiced(BigDecimal.ZERO);
				InterfaceWrapperHelper.save(candToClear);

				alloc.setC_Invoice_Candidate_ID(0);
				InterfaceWrapperHelper.save(alloc);
			}
		}
	}	

}
