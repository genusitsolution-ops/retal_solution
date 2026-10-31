package com.genusit.prms.di

import com.genusit.prms.platform.SessionStore
import com.genusit.prms.data.repository.AgentRepositoryImpl
import com.genusit.prms.data.repository.AllocationRepositoryImpl
import com.genusit.prms.data.repository.AuthRepositoryImpl
import com.genusit.prms.data.repository.BillingRepositoryImpl
import com.genusit.prms.data.repository.CommonRepositoryImpl
import com.genusit.prms.data.repository.PropertyRepositoryImpl
import com.genusit.prms.data.repository.QueryRepositoryImpl
import com.genusit.prms.data.repository.ReportRepositoryImpl
import com.genusit.prms.data.repository.SubscriptionRepositoryImpl
import com.genusit.prms.data.repository.TeamRepositoryImpl
import com.genusit.prms.data.repository.TenantAdminRepositoryImpl
import com.genusit.prms.data.repository.TenantPortalRepositoryImpl
import com.genusit.prms.domain.repository.AgentRepository
import com.genusit.prms.domain.repository.AllocationRepository
import com.genusit.prms.domain.repository.AuthRepository
import com.genusit.prms.domain.repository.BillingRepository
import com.genusit.prms.domain.repository.CommonRepository
import com.genusit.prms.domain.repository.PropertyRepository
import com.genusit.prms.domain.repository.QueryRepository
import com.genusit.prms.domain.repository.ReportRepository
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.repository.SubscriptionRepository
import com.genusit.prms.domain.repository.TeamRepository
import com.genusit.prms.domain.repository.TenantAdminRepository
import com.genusit.prms.domain.repository.TenantPortalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds every domain repository contract to its data-layer implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun session(impl: SessionStore): SessionRepository
    @Binds @Singleton abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun common(impl: CommonRepositoryImpl): CommonRepository
    @Binds @Singleton abstract fun public(impl: com.genusit.prms.data.repository.PublicRepositoryImpl): com.genusit.prms.domain.repository.PublicRepository
    @Binds @Singleton abstract fun properties(impl: PropertyRepositoryImpl): PropertyRepository
    @Binds @Singleton abstract fun tenantAdmin(impl: TenantAdminRepositoryImpl): TenantAdminRepository
    @Binds @Singleton abstract fun billing(impl: BillingRepositoryImpl): BillingRepository
    @Binds @Singleton abstract fun allocations(impl: AllocationRepositoryImpl): AllocationRepository
    @Binds @Singleton abstract fun team(impl: TeamRepositoryImpl): TeamRepository
    @Binds @Singleton abstract fun queries(impl: QueryRepositoryImpl): QueryRepository
    @Binds @Singleton abstract fun reports(impl: ReportRepositoryImpl): ReportRepository
    @Binds @Singleton abstract fun subscription(impl: SubscriptionRepositoryImpl): SubscriptionRepository
    @Binds @Singleton abstract fun agent(impl: AgentRepositoryImpl): AgentRepository
    @Binds @Singleton abstract fun tenantPortal(impl: TenantPortalRepositoryImpl): TenantPortalRepository
    @Binds @Singleton abstract fun ownerSettlements(impl: com.genusit.prms.data.repository.OwnerSettlementRepositoryImpl): com.genusit.prms.domain.repository.OwnerSettlementRepository
    @Binds @Singleton abstract fun photos(impl: com.genusit.prms.data.repository.PhotoRepositoryImpl): com.genusit.prms.domain.repository.PhotoRepository
    @Binds @Singleton abstract fun agentSettlements(impl: com.genusit.prms.data.repository.AgentSettlementRepositoryImpl): com.genusit.prms.domain.repository.AgentSettlementRepository
}
