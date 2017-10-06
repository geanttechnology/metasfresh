package de.metas.material.dispo.service.candidatechangehandler;

import java.math.BigDecimal;

import de.metas.material.dispo.Candidate;
import de.metas.material.event.EventDescr;
import de.metas.material.event.MaterialDemandDescr;
import de.metas.material.event.MaterialDemandEvent;
import de.metas.material.event.MaterialDescriptor;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/*
 * #%L
 * metasfresh-material-dispo-service
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

@UtilityClass
public class MaterialDemandEventCreator
{
	public MaterialDemandEvent createMaterialDemandEvent(
			@NonNull final Candidate demandCandidate, 
			@NonNull final BigDecimal requiredAdditionalQty)
	{
		final int orderLineId = demandCandidate.getDemandDetail() == null ? 0 : demandCandidate.getDemandDetail().getOrderLineId();
		
		final MaterialDemandEvent materialDemandEvent = MaterialDemandEvent
				.builder()
				.materialDemandDescr(MaterialDemandDescr.builder()
						.eventDescr(new EventDescr(demandCandidate.getClientId(), demandCandidate.getOrgId()))
						.materialDescr(createMaterialDescriptorForCandidateAndQty(demandCandidate, requiredAdditionalQty))
						.reference(demandCandidate.getReference())
						.orderLineId(orderLineId)
						.build())
				.build();
		return materialDemandEvent;
	}
	

	private MaterialDescriptor createMaterialDescriptorForCandidateAndQty(
			@NonNull final Candidate candidate, 
			@NonNull final BigDecimal qty)
	{
		return MaterialDescriptor.builder()
				.productId(candidate.getProductId())
				.date(candidate.getDate())
				.qty(qty)
				.warehouseId(candidate.getWarehouseId())
				.build();
	}	
}
